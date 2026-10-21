package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.archivo.ArchivoService;
import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import com.cunoc.puentes.mantenimiento.dto.CierreOrdenDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** El llamador ya tiene el mismo bloqueo de fila usado para subir evidencia. */
@Service
class CierreOrdenService {
  private final ArchivoService archivos;

  CierreOrdenService(ArchivoService archivos) {
    this.archivos = archivos;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
  @Auditable(
      accion = Acciones.CAMBIAR_ESTADO,
      entidad = "orden_mantenimiento",
      entidadId = "#orden.id")
  public void cerrar(OrdenMantenimiento orden, CierreOrdenDTO datos) {
    orden.cambiarEstado(
        EstadoOrden.EJECUTADA,
        new DatosTransicionOrden(
            datos.fechaEjecucion(),
            datos.responsable(),
            archivos.contarEvidencias(orden.getId()),
            null));
  }
}
