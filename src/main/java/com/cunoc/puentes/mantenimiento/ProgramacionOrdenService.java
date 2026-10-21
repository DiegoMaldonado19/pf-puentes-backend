package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** La fachada ya cargó y bloqueó la fila; la fecha no cambia el ciclo de vida. */
@Service
class ProgramacionOrdenService {

  @Transactional(propagation = Propagation.MANDATORY)
  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
  @Auditable(
      accion = Acciones.MODIFICAR,
      entidad = "orden_mantenimiento",
      entidadId = "#orden.id",
      condicion = "#result")
  public boolean programar(OrdenMantenimiento orden, LocalDate fecha) {
    if (orden.getEstado() != EstadoOrden.PROGRAMADA) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "programacion-de-orden-no-editable",
          "La programación no se puede modificar",
          "La fecha solo se registra o cambia en órdenes PROGRAMADA.");
    }
    if (fecha == null) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "fecha-programada-requerida",
          "Fecha programada requerida",
          "Debe indicar la fecha programada de la intervención.");
    }
    if (fecha.equals(orden.getFechaProgramada())) return false;
    orden.setFechaProgramada(fecha);
    return true;
  }
}
