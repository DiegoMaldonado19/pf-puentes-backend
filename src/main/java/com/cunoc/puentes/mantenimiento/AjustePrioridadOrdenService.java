package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Ajuste manual auditado después del bloqueo. No calcula ni reemplaza la sugerencia. */
@Service
class AjustePrioridadOrdenService {

  @Transactional(propagation = Propagation.MANDATORY)
  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
  @Auditable(
      accion = Acciones.MODIFICAR,
      entidad = "orden_mantenimiento",
      entidadId = "#orden.id",
      condicion = "#result")
  public boolean ajustar(OrdenMantenimiento orden, Prioridad prioridad, String justificacion) {
    // Actores y ventana confirmados por el usuario, no definidos literalmente por RN-MTO-06.
    if (orden.getEstado() != EstadoOrden.PROPUESTA
        && orden.getEstado() != EstadoOrden.PROGRAMADA
        && orden.getEstado() != EstadoOrden.EN_EJECUCION) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "prioridad-de-orden-no-editable",
          "La prioridad ya no se puede modificar",
          "Solo se ajusta prioridad en PROPUESTA, PROGRAMADA o EN_EJECUCION.");
    }
    boolean cambio = orden.getPrioridad() != prioridad;
    orden.ajustarPrioridad(prioridad, justificacion);
    return cambio;
  }
}
