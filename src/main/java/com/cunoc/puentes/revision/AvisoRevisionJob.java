package com.cunoc.puentes.revision;

import java.time.Clock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class AvisoRevisionJob {
  private final AvisoRevisionService servicio;
  private final Clock reloj;

  @Autowired
  AvisoRevisionJob(AvisoRevisionService servicio) {
    this(servicio, Clock.systemUTC());
  }

  AvisoRevisionJob(AvisoRevisionService servicio, Clock reloj) {
    this.servicio = servicio;
    this.reloj = reloj;
  }

  @Scheduled(cron = "0 30 6 * * *", zone = "America/Guatemala")
  @SchedulerLock(name = "avisos-revision-vencida", lockAtMostFor = "PT30M")
  public void ejecutar() {
    servicio.avisar(reloj.instant());
  }
}
