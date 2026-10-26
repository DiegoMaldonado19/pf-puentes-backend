package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.inspeccion.InspeccionPublicada;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrato 4, RN-MTO-02: escucha síncronamente {@link InspeccionPublicada} dentro de la transacción
 * de publicación de P5 y genera las propuestas de mantenimiento correspondientes a los daños
 * registrados. Si falla, la transacción completa se revierte.
 */
@Component
class PropuestasInspeccionPublicadaListener {

  private final GeneracionPropuestasMantenimientoService generador;

  PropuestasInspeccionPublicadaListener(GeneracionPropuestasMantenimientoService generador) {
    this.generador = generador;
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void alPublicar(InspeccionPublicada evento) {
    generador.generarDesdeInspeccionPublicada(evento);
  }
}
