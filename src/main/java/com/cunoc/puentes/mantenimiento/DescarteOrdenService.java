package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** El llamador bloqueó la fila antes de que el aspecto tome su instantánea anterior. */
@Service
class DescarteOrdenService {

  @Transactional(propagation = Propagation.MANDATORY)
  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
  @Auditable(
      accion = Acciones.CAMBIAR_ESTADO,
      entidad = "orden_mantenimiento",
      entidadId = "#orden.id")
  public void descartar(OrdenMantenimiento orden, String motivo) {
    orden.cambiarEstado(EstadoOrden.DESCARTADA, new DatosTransicionOrden(null, null, 0, motivo));
  }
}
