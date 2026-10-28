package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.archivo.ArchivoService;
import com.cunoc.puentes.archivo.dto.ArchivoDTO;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.inspeccion.dto.AcompananteDTO;
import com.cunoc.puentes.inspeccion.dto.HistorialTransicionDTO;
import com.cunoc.puentes.inspeccion.dto.InspeccionDTO;
import com.cunoc.puentes.inspeccion.dto.InspeccionResumenDTO;
import com.cunoc.puentes.inspeccion.dto.PuenteInspeccionableDTO;
import com.cunoc.puentes.usuario.Rol;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ConsultaInspeccionService {

  private final InspeccionRepository inspecciones;
  private final TransicionInspeccionRepository transiciones;
  private final AcompananteInspeccionRepository acompanantes;
  private final ReferenciasInspeccion referencias;
  private final InspeccionEnsamblador ensamblador;
  private final ArchivoService archivoService;

  ConsultaInspeccionService(
      InspeccionRepository inspecciones,
      TransicionInspeccionRepository transiciones,
      AcompananteInspeccionRepository acompanantes,
      ReferenciasInspeccion referencias,
      InspeccionEnsamblador ensamblador,
      ArchivoService archivoService) {
    this.inspecciones = inspecciones;
    this.transiciones = transiciones;
    this.acompanantes = acompanantes;
    this.referencias = referencias;
    this.ensamblador = ensamblador;
    this.archivoService = archivoService;
  }

  @Transactional(readOnly = true)
  Page<PuenteInspeccionableDTO> puentesInspeccionables(UUID actorId, Rol rol, Pageable pagina) {
    Pageable acotada = pagina(pagina, "codigo", "id");
    if (rol == Rol.ESTUDIANTE) {
      return referencias.inspectablesDeEstudiante(actorId, acotada);
    }
    return referencias.inspectablesActivos(actorId, acotada);
  }

  @Transactional(readOnly = true)
  Page<InspeccionResumenDTO> listarMias(
      UUID actorId,
      UUID puenteId,
      EstadoInspeccion estado,
      LocalDate desde,
      LocalDate hasta,
      Pageable pagina) {
    Page<Inspeccion> filas =
        inspecciones.buscarMias(
            actorId, puenteId, estado, desde, hasta, pagina(pagina, "creadoEn", "id"));
    return aResumen(filas);
  }

  @Transactional(readOnly = true)
  Page<InspeccionResumenDTO> listarDelPuente(UUID puenteId, UUID actorId, Pageable pagina) {
    referencias.puente(puenteId).orElseThrow(ConsultaInspeccionService::noEncontrada);
    return aResumen(inspecciones.buscarDelPuente(puenteId, actorId, paginaFecha(pagina)));
  }

  @Transactional(readOnly = true)
  InspeccionDTO detalle(UUID inspeccionId, UUID actorId, Rol rol) {
    Inspeccion inspeccion = visible(inspeccionId, actorId, rol);
    return ensamblador.detalle(
        inspeccion,
        referencias.puente(inspeccion.getPuenteId()).orElse(null),
        referencias
            .codigosFormulario(Set.of(inspeccion.getFormularioVersionId()))
            .get(inspeccion.getFormularioVersionId()),
        acompanantes.findByInspeccion_Id(inspeccion.getId()).stream()
            .map(fila -> new AcompananteDTO(fila.getNombre(), fila.getUsuarioId()))
            .toList(),
        superadaPor(List.of(inspeccion)).get(inspeccion.getId()),
        List.of());
  }

  @Transactional(readOnly = true)
  List<HistorialTransicionDTO> historial(UUID inspeccionId, UUID actorId, Rol rol) {
    visible(inspeccionId, actorId, rol);
    return transiciones.findByInspeccion_IdOrderByCreadoEnAsc(inspeccionId).stream()
        .map(
            fila ->
                new HistorialTransicionDTO(
                    fila.getId(),
                    fila.getEstadoOrigen(),
                    fila.getEstadoDestino(),
                    fila.getActorId(),
                    fila.getMotivo(),
                    fila.getCreadoEn()))
        .toList();
  }

  @Transactional(readOnly = true)
  List<ArchivoDTO> archivos(UUID inspeccionId, UUID actorId, Rol rol) {
    visible(inspeccionId, actorId, rol);
    return archivoService.listar(inspeccionId);
  }

  private Inspeccion visible(UUID inspeccionId, UUID actorId, Rol rol) {
    Inspeccion inspeccion =
        inspecciones
            .findByIdAndEliminadoEnIsNull(inspeccionId)
            .orElseThrow(ConsultaInspeccionService::noEncontrada);
    UUID catedratico = referencias.catedraticoDeCurso(inspeccion.getCursoId()).orElse(null);
    if (!VisibilidadInspeccion.visible(inspeccion, actorId, rol, catedratico)) {
      throw noEncontrada();
    }
    return inspeccion;
  }

  private Page<InspeccionResumenDTO> aResumen(Page<Inspeccion> filas) {
    Map<UUID, ReferenciasInspeccion.PuenteVista> puentes =
        referencias.puentes(
            filas.getContent().stream().map(Inspeccion::getPuenteId).collect(Collectors.toSet()));
    Map<UUID, UUID> superadas = superadaPor(filas.getContent());
    return filas.map(
        inspeccion ->
            ensamblador.resumen(
                inspeccion,
                puentes.get(inspeccion.getPuenteId()),
                superadas.get(inspeccion.getId())));
  }

  private Map<UUID, UUID> superadaPor(List<Inspeccion> filas) {
    Set<UUID> ids = filas.stream().map(Inspeccion::getId).collect(Collectors.toSet());
    if (ids.isEmpty()) {
      return Map.of();
    }
    return inspecciones.findPublicadasQueCorrigen(ids).stream()
        .collect(
            Collectors.toMap(
                correccion -> correccion.getSupersedeA().getId(), Inspeccion::getId, (a, b) -> a));
  }

  private static Pageable pagina(Pageable pagina, String... orden) {
    return PageRequest.of(
        pagina.getPageNumber(),
        Math.min(Math.max(pagina.getPageSize(), 1), 100),
        Sort.by(Sort.Direction.DESC, orden));
  }

  private static Pageable paginaFecha(Pageable pagina) {
    return PageRequest.of(
        pagina.getPageNumber(),
        Math.min(Math.max(pagina.getPageSize(), 1), 100),
        Sort.by(Sort.Direction.DESC, "fechaInspeccion", "id"));
  }

  static NegocioException noEncontrada() {
    return new NegocioException(
        HttpStatus.NOT_FOUND,
        "inspeccion-no-encontrada",
        "Inspección no encontrada",
        "La inspección no existe o no está visible para su cuenta.");
  }
}
