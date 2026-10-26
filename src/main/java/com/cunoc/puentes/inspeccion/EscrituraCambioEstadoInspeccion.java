package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Paso interno auditado: la fachada ya autorizó el cambio en su transacción. */
@Service
class EscrituraCambioEstadoInspeccion {

  private final InspeccionRepository inspecciones;
  private final TransicionInspeccionRepository transiciones;
  private final EntityManager em;

  EscrituraCambioEstadoInspeccion(
      InspeccionRepository inspecciones,
      TransicionInspeccionRepository transiciones,
      EntityManager em) {
    this.inspecciones = inspecciones;
    this.transiciones = transiciones;
    this.em = em;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @Auditable(
      accion = Acciones.CAMBIAR_ESTADO,
      entidad = "inspeccion",
      entidadId = "#inspeccion.id",
      motivo = "#datos.motivo()")
  public void aplicar(
      Inspeccion inspeccion,
      EstadoInspeccion destino,
      UUID actorId,
      DatosTransicionInspeccion datos) {
    EstadoInspeccion origen = inspeccion.getEstado();
    inspeccion.cambiarEstado(destino, datos);
    TransicionInspeccion fila = new TransicionInspeccion();
    fila.setInspeccion(inspeccion);
    fila.setEstadoOrigen(origen);
    fila.setEstadoDestino(destino);
    fila.setActorId(actorId);
    fila.setRevisionId(datos.revisionId());
    fila.setMotivo(blancoANulo(datos.motivo()));
    transiciones.save(fila);
    inspecciones.save(inspeccion);
    em.flush();
  }

  private static String blancoANulo(String motivo) {
    return motivo == null || motivo.isBlank() ? null : motivo;
  }
}
