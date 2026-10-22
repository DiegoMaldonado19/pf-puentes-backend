package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.notificacion.NotificacionService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RN-MTO-07: recordatorio y marca de envío se confirman juntos; no crea ni acepta órdenes. */
@Service
public class RecordatoriosRutinariosService {
  private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/uuuu");
  private final RecordatoriosRutinariosRepository avisos;
  private final SeguimientoRutinarioRepository seguimiento;
  private final NotificacionService notificaciones;

  RecordatoriosRutinariosService(
      RecordatoriosRutinariosRepository avisos,
      SeguimientoRutinarioRepository seguimiento,
      NotificacionService notificaciones) {
    this.avisos = avisos;
    this.seguimiento = seguimiento;
    this.notificaciones = notificaciones;
  }

  @Transactional
  public int recordar(UUID puenteId, LocalDate hoy) {
    if (puenteId == null || hoy == null)
      throw new IllegalArgumentException("Puente y fecha de evaluación son obligatorios.");
    var puente = avisos.bloquear(puenteId);
    if (puente.isEmpty()) return 0;
    var base = seguimiento.base(puenteId).orElseThrow();
    LocalDate referencia = base.ejecucion() == null ? base.registro() : base.ejecucion();
    LocalDate limite = referencia.plusYears(1);
    if (seguimiento.contar(puenteId, hoy, limite).enPlazo() > 0) return 0;

    int enviadas = 0;
    var destinatarios = avisos.destinatariosPendientes(puenteId, referencia);
    while (!destinatarios.isEmpty()) {
      for (UUID usuario : destinatarios) {
        notificaciones.notificar(
            usuario,
            "RUTINARIO_SIN_PROGRAMAR",
            "El puente "
                + puente.get().codigo()
                + " ("
                + puente.get().nombre()
                + ") no tiene mantenimiento rutinario programado dentro del plazo anual. Fecha límite: "
                + FECHA.format(limite)
                + ". Revisa la programación de sus órdenes.",
            "/puentes/" + puenteId);
        avisos.registrar(puenteId, usuario, referencia);
        enviadas++;
      }
      destinatarios = avisos.destinatariosPendientes(puenteId, referencia);
    }
    return enviadas;
  }
}
