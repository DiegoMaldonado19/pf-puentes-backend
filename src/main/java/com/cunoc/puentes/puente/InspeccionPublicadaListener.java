package com.cunoc.puentes.puente;

import com.cunoc.puentes.inspeccion.InspeccionPublicada;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrato 4, RN-INV-09: síncrono y en la transacción de P5. Si falla, la publicación se revierte:
 * nunca queda una inspección publicada sin sus campos derivados.
 */
@Component
class InspeccionPublicadaListener {

  private final EntityManager em;
  private final CamposDerivadosService camposDerivados;

  InspeccionPublicadaListener(EntityManager em, CamposDerivadosService camposDerivados) {
    this.em = em;
    this.camposDerivados = camposDerivados;
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void alPublicar(InspeccionPublicada evento) {
    if (evento.puenteId() == null
        || evento.estado() == null
        || evento.indiceCondicion() == null
        || evento.fecha() == null) {
      throw new IllegalArgumentException(
          "InspeccionPublicada %s llegó incompleta".formatted(evento.inspeccionId()));
    }
    Puente puente = em.find(Puente.class, evento.puenteId());
    if (puente == null) {
      throw new IllegalStateException(
          "InspeccionPublicada %s apunta al puente inexistente %s"
              .formatted(evento.inspeccionId(), evento.puenteId()));
    }
    // Bloquea y relee: dos publicaciones simultáneas del mismo puente se aplican en orden
    em.refresh(puente, LockModeType.PESSIMISTIC_WRITE);
    // RN-INV-09: manda la inspección más reciente; una atrasada (p. ej. sincronizada offline
    // después) no pisa a una posterior
    if (puente.getFechaUltimaInspeccion() != null
        && evento.fecha().isBefore(puente.getFechaUltimaInspeccion())) {
      return;
    }
    camposDerivados.actualizar(evento);
  }
}
