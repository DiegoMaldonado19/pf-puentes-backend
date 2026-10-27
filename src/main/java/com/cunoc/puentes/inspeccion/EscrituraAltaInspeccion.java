package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Alta auditada en transacción propia: un reintento concurrente no marca rollback la petición. */
@Service
class EscrituraAltaInspeccion {

  private final InspeccionRepository inspecciones;
  private final TransicionInspeccionRepository transiciones;
  private final EntityManager em;

  EscrituraAltaInspeccion(
      InspeccionRepository inspecciones,
      TransicionInspeccionRepository transiciones,
      EntityManager em) {
    this.inspecciones = inspecciones;
    this.transiciones = transiciones;
    this.em = em;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Auditable(accion = Acciones.CREAR, entidad = "inspeccion", entidadId = "#inspeccion.id")
  public Inspeccion persistir(Inspeccion inspeccion) {
    return escribir(inspeccion);
  }

  /** G7.1 es dueño de la transacción; dominio, transición y auditoría participan en ella. */
  @Transactional(propagation = Propagation.MANDATORY)
  @Auditable(accion = Acciones.CREAR, entidad = "inspeccion", entidadId = "#inspeccion.id")
  public Inspeccion persistirParticipando(Inspeccion inspeccion) {
    return escribir(inspeccion);
  }

  private Inspeccion escribir(Inspeccion inspeccion) {
    Inspeccion guardada = inspecciones.save(inspeccion);
    TransicionInspeccion alta = new TransicionInspeccion();
    alta.setInspeccion(guardada);
    alta.setEstadoOrigen(null);
    alta.setEstadoDestino(EstadoInspeccion.BORRADOR);
    alta.setActorId(guardada.getAutorId());
    transiciones.save(alta);
    em.flush();
    return guardada;
  }
}
