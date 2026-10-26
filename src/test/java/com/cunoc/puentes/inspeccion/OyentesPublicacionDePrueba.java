package com.cunoc.puentes.inspeccion;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dobles de P8/P9 para US-P5-08: registran la reacción o fallan a propósito. Deben ser {@code
 * MANDATORY} como los oyentes reales.
 */
@Component
class OyentesPublicacionDePrueba {

  static final AtomicBoolean fallarMantenimiento = new AtomicBoolean(false);
  static final List<UUID> propuestas = new CopyOnWriteArrayList<>();
  static final List<UUID> hilos = new CopyOnWriteArrayList<>();

  static void reiniciar() {
    fallarMantenimiento.set(false);
    propuestas.clear();
    hilos.clear();
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  void mantenimiento(InspeccionPublicada evento) {
    if (fallarMantenimiento.get()) {
      throw new IllegalStateException("El oyente de mantenimiento de prueba falló.");
    }
    propuestas.add(evento.inspeccionId());
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  void foro(InspeccionPublicada evento) {
    hilos.add(evento.inspeccionId());
  }
}
