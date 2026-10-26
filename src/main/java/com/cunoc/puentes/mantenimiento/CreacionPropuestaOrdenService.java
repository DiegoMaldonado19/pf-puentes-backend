package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paso interno transaccional y auditado para persistir propuestas generadas automáticamente
 * (RN-MTO-02, RN-MTO-07).
 */
@Service
class CreacionPropuestaOrdenService {

  private final OrdenMantenimientoRepository ordenes;

  CreacionPropuestaOrdenService(OrdenMantenimientoRepository ordenes) {
    this.ordenes = ordenes;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @Auditable(accion = Acciones.CREAR, entidad = "orden_mantenimiento", entidadId = "#result.id")
  public OrdenMantenimiento crearPorDano(
      UUID puenteId, UUID inspeccionId, PropuestaIntervencion propuesta) {
    OrdenMantenimiento orden = new OrdenMantenimiento();
    orden.setPuenteId(puenteId);
    orden.setInspeccionId(inspeccionId);
    orden.setTipo(propuesta.tipo());
    orden.setDescripcion(propuesta.descripcion());
    orden.setElementoRef(propuesta.elementoRef());
    orden.setPrioridadSugerida(propuesta.prioridadSugerida());
    orden.setPrioridad(propuesta.prioridadSugerida());
    return ordenes.save(orden);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @Auditable(accion = Acciones.CREAR, entidad = "orden_mantenimiento", entidadId = "#result.id")
  public OrdenMantenimiento crearRutinariaAnual(UUID puenteId, String descripcion) {
    OrdenMantenimiento orden = new OrdenMantenimiento();
    orden.setPuenteId(puenteId);
    orden.setTipo(TipoOrden.RUTINARIO);
    orden.setDescripcion(descripcion);
    orden.setPrioridadSugerida(Prioridad.MEDIA);
    orden.setPrioridad(Prioridad.MEDIA);
    return ordenes.save(orden);
  }
}

