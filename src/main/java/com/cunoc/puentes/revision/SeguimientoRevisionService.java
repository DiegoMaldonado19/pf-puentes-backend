package com.cunoc.puentes.revision;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import com.cunoc.puentes.usuario.EstadoCuentaService;
import com.cunoc.puentes.usuario.Rol;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeguimientoRevisionService {
  private final SeguimientoRevisionRepository contexto;
  private final RevisionRepository revisiones;
  private final ObservacionRevisionRepository observaciones;
  private final EstadoCuentaService cuentas;

  SeguimientoRevisionService(
      SeguimientoRevisionRepository contexto,
      RevisionRepository revisiones,
      ObservacionRevisionRepository observaciones,
      EstadoCuentaService cuentas) {
    this.contexto = contexto;
    this.revisiones = revisiones;
    this.observaciones = observaciones;
    this.cuentas = cuentas;
  }

  @Transactional(readOnly = true)
  public Page<SeguimientoRevisionRepository.Pendiente> pendientes(UUID actor, Pageable pagina) {
    if (cuentas.rolSiEstaActiva(actor).filter(Rol.ESTUDIANTE::equals).isEmpty()) throw prohibido();
    return contexto.pendientes(actor, pagina(pagina));
  }

  @Transactional(readOnly = true)
  public Page<SeguimientoRevisionRepository.InspeccionRevisada> inspecciones(
      UUID actor, Pageable pagina) {
    if (cuentas.rolSiEstaActiva(actor).filter(Rol.ESTUDIANTE::equals).isEmpty()) throw prohibido();
    return contexto.inspecciones(actor, pagina(pagina));
  }

  @Transactional
  @Auditable(accion = Acciones.MODIFICAR, entidad = "observacion_anclada", entidadId = "#id")
  public ObservacionRevisionDTO resolver(UUID actor, UUID id) {
    activo(actor);
    UUID inspeccion =
        contexto.inspeccionDeObservacion(id).orElseThrow(SeguimientoRevisionService::noEncontrada);
    var datos =
        contexto.contexto(inspeccion, true).orElseThrow(SeguimientoRevisionService::noEncontrada);
    if (!datos.autorId().equals(actor)) throw noEncontrada();
    ObservacionAnclada fila =
        observaciones.findById(id).orElseThrow(SeguimientoRevisionService::noEncontrada);
    if (fila.getRevision().getEmitidaEn() == null) throw noEncontrada();
    if (fila.getResueltaEn() != null) return ObservacionRevisionDTO.de(fila);
    if (!datos.estado().equals("BORRADOR")) {
      throw error(
          HttpStatus.CONFLICT,
          "inspeccion-no-editable",
          "Solo puedes resolver pendientes de un borrador propio después de corregirlo.");
    }
    fila.setResueltaEn(Instant.now());
    return ObservacionRevisionDTO.de(observaciones.saveAndFlush(fila));
  }

  @Transactional(readOnly = true)
  public Page<HistorialRevisionDTO> historial(UUID actor, UUID inspeccion, Pageable pagina) {
    Rol rol = cuentas.rolSiEstaActiva(actor).orElseThrow(SeguimientoRevisionService::prohibido);
    var datos =
        contexto.contexto(inspeccion, false).orElseThrow(SeguimientoRevisionService::noEncontrada);
    if (!actor.equals(datos.autorId())
        && (rol != Rol.CATEDRATICO
            || (!actor.equals(datos.catedraticoId()) && !contexto.fueRevisor(actor, inspeccion))))
      throw noEncontrada();
    // El autor no ve borradores de revisión aún no emitidos; el ciclo se muestra sin notas
    // privadas.
    return revisiones
        .findByInspeccionIdOrderByCreadoEnDescIdDesc(inspeccion, pagina(pagina))
        .map(
            revision -> {
              boolean visible =
                  revision.getEmitidaEn() != null || revision.getRevisorId().equals(actor);
              return new HistorialRevisionDTO(
                  revision.getId(),
                  revision.getRevisorId(),
                  revision.getVeredicto(),
                  visible ? revision.getObservacion() : null,
                  revision.getCreadoEn(),
                  revision.getEmitidaEn(),
                  datos.estado(),
                  visible
                      ? observaciones
                          .findByRevision_IdOrderByCreadoEnAscIdAsc(revision.getId())
                          .stream()
                          .map(ObservacionRevisionDTO::de)
                          .toList()
                      : java.util.List.of());
            });
  }

  @Transactional(readOnly = true)
  public CalificacionRevisionDTO calificacion(UUID actor, UUID id) {
    activo(actor);
    Revision revision =
        revisiones.findById(id).orElseThrow(SeguimientoRevisionService::noEncontrada);
    var datos =
        contexto
            .contexto(revision.getInspeccionId(), false)
            .orElseThrow(SeguimientoRevisionService::noEncontrada);
    if (!actor.equals(datos.autorId()) && !actor.equals(revision.getRevisorId()))
      throw noEncontrada();
    return new CalificacionRevisionDTO(revision.getCalificacion());
  }

  @Transactional
  @Auditable(accion = Acciones.MODIFICAR, entidad = "revision", entidadId = "#id")
  public CalificacionRevisionDTO calificar(UUID actor, UUID id, CalificacionRevisionDTO datos) {
    if (cuentas.rolSiEstaActiva(actor).filter(Rol.CATEDRATICO::equals).isEmpty()) throw prohibido();
    UUID inspeccion =
        contexto
            .inspeccionDelRevisor(actor, id)
            .orElseThrow(SeguimientoRevisionService::noEncontrada);
    var contextoActual =
        contexto.contexto(inspeccion, true).orElseThrow(SeguimientoRevisionService::noEncontrada);
    if (!actor.equals(contextoActual.catedraticoId()) || actor.equals(contextoActual.autorId()))
      throw noEncontrada();
    BigDecimal nota = datos.calificacion();
    if (nota != null
        && (nota.signum() < 0 || nota.compareTo(new BigDecimal("100")) > 0 || nota.scale() > 2)) {
      throw error(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "calificacion-invalida",
          "La calificación admite de 0 a 100 con hasta dos decimales.");
    }
    Revision revision =
        revisiones.findById(id).orElseThrow(SeguimientoRevisionService::noEncontrada);
    revision.setCalificacion(nota);
    revisiones.saveAndFlush(revision);
    return new CalificacionRevisionDTO(nota);
  }

  private void activo(UUID actor) {
    if (cuentas.rolSiEstaActiva(actor).isEmpty()) throw prohibido();
  }

  private static Pageable pagina(Pageable pagina) {
    return PageRequest.of(pagina.getPageNumber(), Math.min(100, pagina.getPageSize()));
  }

  private static NegocioException noEncontrada() {
    return error(
        HttpStatus.NOT_FOUND, "revision-no-encontrada", "El recurso no existe o no te pertenece.");
  }

  private static NegocioException prohibido() {
    return error(
        HttpStatus.FORBIDDEN,
        "cuenta-no-habilitada",
        "La cuenta no está habilitada para esta operación.");
  }

  private static NegocioException error(HttpStatus estado, String tipo, String detalle) {
    return new NegocioException(estado, tipo, "Seguimiento académico", detalle);
  }
}
