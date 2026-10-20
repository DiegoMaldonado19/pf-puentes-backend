package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Paso interno auditado: la fachada ya cargó y bloqueó la orden en su transacción. */
@Service
class AceptacionOrdenService {

  @Transactional(propagation = Propagation.MANDATORY)
  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
  @Auditable(
      accion = Acciones.CAMBIAR_ESTADO,
      entidad = "orden_mantenimiento",
      entidadId = "#orden.id")
  public void aceptar(OrdenMantenimiento orden) {
    orden.cambiarEstado(EstadoOrden.PROGRAMADA, new DatosTransicionOrden(null, null, 0, null));
  }
}
