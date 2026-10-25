package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** El llamador bloqueó la fila antes de que el aspecto tome su instantánea anterior. */
@Service
class DecisionConMotivoOrdenService {

  @Transactional(propagation = Propagation.MANDATORY)
  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
  @Auditable(
      accion = Acciones.CAMBIAR_ESTADO,
      entidad = "orden_mantenimiento",
      entidadId = "#orden.id")
  public void decidir(OrdenMantenimiento orden, EstadoOrden destino, String motivo) {
    if (destino != EstadoOrden.DESCARTADA && destino != EstadoOrden.CANCELADA) {
      throw new IllegalArgumentException("La decisión debe ser descarte o cancelación.");
    }
    orden.cambiarEstado(destino, new DatosTransicionOrden(null, null, 0, motivo));
  }
}
