package com.cunoc.puentes.revision;

import com.cunoc.puentes.archivo.ArchivoService;
import com.cunoc.puentes.archivo.dto.ArchivoDTO;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import com.cunoc.puentes.inspeccion.PublicacionInspeccionService;
import com.cunoc.puentes.inspeccion.VeredictoRevision;
import com.cunoc.puentes.usuario.EstadoCuentaService;
import com.cunoc.puentes.usuario.Rol;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class DecisionRevisionService {
  private final RevisionRepository revisiones;
  private final ObservacionRevisionRepository observaciones;
  private final BandejaRevisionRepository contexto;
  private final EstadoCuentaService cuentas;
  private final PublicacionInspeccionService publicaciones;
  private final ObjectMapper json;
  private final ArchivoService archivos;

  DecisionRevisionService(
      RevisionRepository revisiones,
      ObservacionRevisionRepository observaciones,
      BandejaRevisionRepository contexto,
      EstadoCuentaService cuentas,
      PublicacionInspeccionService publicaciones,
      ObjectMapper json,
      ArchivoService archivos) {
    this.revisiones = revisiones;
    this.observaciones = observaciones;
    this.contexto = contexto;
    this.cuentas = cuentas;
    this.publicaciones = publicaciones;
    this.json = json;
    this.archivos = archivos;
  }

  @Transactional(readOnly = true)
  public DetalleRevisionDTO detalle(UUID actor, UUID id) {
    Revision revision = exigir(actor, id, false);
    var evidencia = contexto.evidencia(revision.getInspeccionId());
    Map<String, Object> esquema = json.readValue(evidencia.esquema(), new TypeReference<>() {});
    Map<String, Object> datos = json.readValue(evidencia.datos(), new TypeReference<>() {});
    return new DetalleRevisionDTO(
        id,
        revision.getInspeccionId(),
        contexto
            .estado(actor, revision.getInspeccionId())
            .orElseThrow(DecisionRevisionService::noEncontrada),
        revision.getVeredicto(),
        revision.getObservacion(),
        esquema,
        datos,
        CamposRevision.extraer(esquema, datos),
        observaciones.findByRevision_IdOrderByCreadoEnAscIdAsc(id).stream()
            .map(ObservacionRevisionDTO::de)
            .toList());
  }

  @Transactional
  @Auditable(accion = Acciones.MODIFICAR, entidad = "revision", entidadId = "#id")
  public DetalleRevisionDTO emitir(UUID actor, UUID id, DecisionRevisionDTO decision) {
    Revision revision = exigir(actor, id, true);
    exigirAbierta(revision);
    if (decision.veredicto() == null
        || (decision.veredicto() != Veredicto.APROBADA
            && (decision.observacion() == null || decision.observacion().isBlank()))) {
      throw error(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "observacion-requerida",
          "Cambios y rechazo exigen una observación escrita no vacía.");
    }
    revision.setVeredicto(decision.veredicto());
    revision.setObservacion(decision.observacion() == null ? null : decision.observacion().strip());
    revision.setEmitidaEn(Instant.now());
    revisiones.saveAndFlush(revision);
    if (decision.veredicto() == Veredicto.APROBADA) {
      publicaciones.publicarPorAprobacion(
          revision.getInspeccionId(),
          actor,
          id,
          decision.estadoConfirmado(),
          decision.justificacionEstado());
    } else {
      publicaciones.emitirVeredicto(
          revision.getInspeccionId(),
          actor,
          id,
          VeredictoRevision.valueOf(decision.veredicto().name()),
          revision.getObservacion(),
          null,
          null);
    }
    return detalle(actor, id);
  }

  @Transactional(readOnly = true)
  public List<ObservacionRevisionDTO> listar(UUID actor, UUID id) {
    exigir(actor, id, false);
    return observaciones.findByRevision_IdOrderByCreadoEnAscIdAsc(id).stream()
        .map(ObservacionRevisionDTO::de)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ArchivoDTO> archivos(UUID actor, UUID id) {
    Revision revision = exigir(actor, id, false);
    return archivos.listar(revision.getInspeccionId());
  }

  @Transactional
  @Auditable(accion = Acciones.CREAR, entidad = "observacion_anclada", entidadId = "#result.id")
  public ObservacionRevisionDTO agregar(UUID actor, UUID id, AgregarObservacionDTO datos) {
    Revision revision = exigir(actor, id, true);
    exigirAbierta(revision);
    if (datos.elementoRef() == null
        || datos.texto() == null
        || datos.texto().isBlank()
        || datos.texto().length() > 10000
        || datos.elementoRef().length() > 200) {
      throw error(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "observacion-invalida",
          "Se requiere una referencia válida y texto no vacío de hasta 10000 caracteres.");
    }
    if (detalle(actor, id).campos().stream()
        .noneMatch(campo -> campo.referencia().equals(datos.elementoRef()))) {
      throw error(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "referencia-invalida",
          "El campo no existe en la versión original del formulario.");
    }
    ObservacionAnclada fila = new ObservacionAnclada();
    fila.setRevision(revision);
    fila.setElementoRef(datos.elementoRef());
    fila.setTexto(datos.texto().strip());
    return ObservacionRevisionDTO.de(observaciones.saveAndFlush(fila));
  }

  private Revision exigir(UUID actor, UUID id, boolean bloquear) {
    if (cuentas.rolSiEstaActiva(actor).filter(Rol.CATEDRATICO::equals).isEmpty()) {
      throw error(
          HttpStatus.FORBIDDEN, "cuenta-no-habilitada", "Se requiere un catedrático activo.");
    }
    UUID inspeccion =
        contexto.inspeccionDeRevision(actor, id).orElseThrow(DecisionRevisionService::noEncontrada);
    var estado =
        bloquear ? contexto.bloquear(actor, inspeccion) : contexto.estado(actor, inspeccion);
    String actual = estado.orElseThrow(DecisionRevisionService::noEncontrada);
    if (bloquear && !actual.equals("EN_REVISION")) {
      throw error(HttpStatus.CONFLICT, "revision-cerrada", "La inspección ya no está en revisión.");
    }
    return revisiones.findById(id).orElseThrow(DecisionRevisionService::noEncontrada);
  }

  private static void exigirAbierta(Revision revision) {
    if (revision.getVeredicto() != null)
      throw error(HttpStatus.CONFLICT, "revision-cerrada", "Este ciclo ya tiene un veredicto.");
  }

  private static NegocioException noEncontrada() {
    return error(
        HttpStatus.NOT_FOUND, "revision-no-encontrada", "La revisión no existe o no te pertenece.");
  }

  private static NegocioException error(HttpStatus status, String tipo, String detalle) {
    return new NegocioException(status, tipo, "Revisión académica", detalle);
  }
}
