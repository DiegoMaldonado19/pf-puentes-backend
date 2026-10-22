package com.cunoc.puentes.mantenimiento;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecordatoriosRutinariosJob {
  private static final Logger LOG = LoggerFactory.getLogger(RecordatoriosRutinariosJob.class);
  private final SeguimientoRutinarioRepository seguimiento;
  private final RecordatoriosRutinariosService servicio;
  private final Clock reloj;

  RecordatoriosRutinariosJob(
      SeguimientoRutinarioRepository seguimiento,
      RecordatoriosRutinariosService servicio,
      @Qualifier("relojMantenimiento") Clock reloj) {
    this.seguimiento = seguimiento;
    this.servicio = servicio;
    this.reloj = reloj;
  }

  @Scheduled(cron = "0 15 6 * * *", zone = "America/Guatemala")
  @SchedulerLock(name = "recordatorios-rutinario-anual")
  public void ejecutar() {
    LocalDate hoy = LocalDate.now(reloj);
    UUID despues = null;
    var puentes = seguimiento.sinProgramacion(despues, hoy);
    while (!puentes.isEmpty()) {
      for (UUID puente : puentes) {
        try {
          servicio.recordar(puente, hoy);
        } catch (RuntimeException error) {
          LOG.error(
              "No se pudo recordar la programación del puente {}; se reintentará en la próxima revisión.",
              puente,
              error);
        }
      }
      despues = puentes.getLast();
      puentes = seguimiento.sinProgramacion(despues, hoy);
    }
  }
}
