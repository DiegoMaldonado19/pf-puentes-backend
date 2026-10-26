package com.cunoc.puentes.inspeccion;

import java.time.Clock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RN-ARC-06 / RN-INS-15: aviso a los 180 días. Corre a las 04:00 GT para no pisar la purga de P1
 * (03:00).
 */
@Component
class AvisoBorradoresAbandonadosJob {

  private final AvisoBorradoresAbandonadosService servicio;
  private final Clock reloj;

  @Autowired
  AvisoBorradoresAbandonadosJob(AvisoBorradoresAbandonadosService servicio) {
    this(servicio, Clock.systemUTC());
  }

  AvisoBorradoresAbandonadosJob(AvisoBorradoresAbandonadosService servicio, Clock reloj) {
    this.servicio = servicio;
    this.reloj = reloj;
  }

  @Scheduled(cron = "0 0 4 * * *", zone = "America/Guatemala")
  @SchedulerLock(name = "aviso-borradores-abandonados")
  public void ejecutar() {
    servicio.avisar(reloj.instant());
  }
}
