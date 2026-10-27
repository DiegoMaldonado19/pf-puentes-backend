package com.cunoc.puentes.foro;

import com.cunoc.puentes.inspeccion.InspeccionPublicada;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ForoInspeccionPublicadaListener {

  private final ForoService foroService;

  ForoInspeccionPublicadaListener(ForoService foroService) {
    this.foroService = foroService;
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void alPublicar(InspeccionPublicada evento) {
    foroService.abrirHiloDeInspeccionPublicada(evento);
  }
}
