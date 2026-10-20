package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.archivo.ArchivoService;
import com.cunoc.puentes.archivo.dto.ArchivoDTO;
import com.cunoc.puentes.archivo.dto.SubirFotoDTO;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.mantenimiento.dto.PaginaEvidenciasDTO;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
public class EvidenciasOrdenService {
  private final OrdenMantenimientoRepository ordenes;
  private final ValidacionOrigenOrden origen;
  private final ConsultaOrdenes consulta;
  private final GuardadoEvidenciaService guardado;
  private final ArchivoService archivos;

  EvidenciasOrdenService(
      OrdenMantenimientoRepository ordenes,
      ValidacionOrigenOrden origen,
      ConsultaOrdenes consulta,
      GuardadoEvidenciaService guardado,
      ArchivoService archivos) {
    this.ordenes = ordenes;
    this.origen = origen;
    this.consulta = consulta;
    this.guardado = guardado;
    this.archivos = archivos;
  }

  /** Ventana de carga confirmada por el equipo: PROGRAMADA y EN_EJECUCION. */
  @Transactional
  public ArchivoDTO subir(UUID puenteId, UUID ordenId, SubirFotoDTO datos) {
    OrdenMantenimiento orden =
        ordenes
            .bloquear(puenteId, ordenId)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "orden-no-encontrada",
                        "Orden no encontrada",
                        "La orden indicada no existe en el puente solicitado."));
    origen.validar(puenteId, orden.getInspeccionId());
    if (orden.getEstado() != EstadoOrden.PROGRAMADA
        && orden.getEstado() != EstadoOrden.EN_EJECUCION) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "evidencia-de-orden-no-editable",
          "La orden no admite nuevas fotos",
          "Solo se sube evidencia en PROGRAMADA o EN_EJECUCION.");
    }
    return guardado.guardar(ordenId, datos, !ordenes.existeArchivo(datos.id()));
  }

  /** La evidencia sigue consultable al cerrar; solo se firman fotos no purgadas. */
  @Transactional(readOnly = true)
  public PaginaEvidenciasDTO listar(UUID puenteId, UUID ordenId, int pagina, int tamano) {
    if (pagina < 0 || tamano < 1 || tamano > 100 || (long) pagina * tamano > Integer.MAX_VALUE) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "pagina-de-evidencias-invalida",
          "Paginación inválida",
          "La página debe ser no negativa y el tamaño estar entre 1 y 100.");
    }
    consulta.obtener(puenteId, ordenId);
    return PaginaEvidenciasDTO.de(
        puenteId,
        ordenId,
        archivos.listarEvidencias(
            ordenId, PageRequest.of(pagina, tamano, Sort.by("creadoEn", "id"))));
  }
}
