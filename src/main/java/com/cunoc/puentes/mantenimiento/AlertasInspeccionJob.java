package com.cunoc.puentes.mantenimiento;

import java.time.Clock;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * DT-BE-14: revisión diaria y bloqueo distribuido; el servicio controla duplicados persistentes.
 */
@Component
public class AlertasInspeccionJob {
  private static final Logger LOG = LoggerFactory.getLogger(AlertasInspeccionJob.class);
  private final AlertasInspeccionRepository repositorio;
  private final AlertasInspeccionService servicio;
  private final Clock reloj;

  @Autowired
  AlertasInspeccionJob(AlertasInspeccionRepository repositorio, AlertasInspeccionService servicio) {
    this(repositorio, servicio, Clock.systemUTC());
  }

  AlertasInspeccionJob(
      AlertasInspeccionRepository repositorio, AlertasInspeccionService servicio, Clock reloj) {
    this.repositorio = repositorio;
    this.servicio = servicio;
    this.reloj = reloj;
  }

  @Scheduled(cron = "0 0 6 * * *", zone = "America/Guatemala")
  @SchedulerLock(name = "alertas-inspeccion-vencida")
  public void ejecutar() {
    var ahora = reloj.instant();
    UUID despues = null;
    var puentes = repositorio.siguientes(despues, ahora);
    while (!puentes.isEmpty()) {
      for (UUID puente : puentes) {
        try {
          servicio.alertar(puente, ahora);
        } catch (RuntimeException error) {
          LOG.error(
              "No se pudo generar la alerta del puente {}; se reintentará en la próxima revisión.",
              puente,
              error);
        }
      }
      despues = puentes.getLast();
      puentes = repositorio.siguientes(despues, ahora);
    }
  }
}
