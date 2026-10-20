package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Inicio interno auditado: la fachada ya cargó y bloqueó la orden en su transacción. */
@Service
class InicioEjecucionOrdenService {

  @Transactional(propagation = Propagation.MANDATORY)
  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
  @Auditable(
      accion = Acciones.CAMBIAR_ESTADO,
      entidad = "orden_mantenimiento",
      entidadId = "#orden.id")
  public void iniciar(OrdenMantenimiento orden) {
    orden.cambiarEstado(EstadoOrden.EN_EJECUCION, new DatosTransicionOrden(null, null, 0, null));
  }
}
