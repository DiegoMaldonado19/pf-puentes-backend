package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.notificacion.NotificacionService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RN-MTO-08: una transacción por puente; aviso P9 y marca de envío se confirman juntos. */
@Service
public class AlertasInspeccionService {
  private final AlertasInspeccionRepository repositorio;
  private final NotificacionService notificaciones;

  AlertasInspeccionService(
      AlertasInspeccionRepository repositorio, NotificacionService notificaciones) {
    this.repositorio = repositorio;
    this.notificaciones = notificaciones;
  }

  @Transactional
  public int alertar(UUID puenteId, Instant ahora) {
    if (puenteId == null || ahora == null)
      throw new IllegalArgumentException("Puente y fecha de evaluación son obligatorios.");
    if (!repositorio.bloquear(puenteId)) return 0;
    var referencia = repositorio.referenciaVencida(puenteId, ahora);
    if (referencia == null) return 0;
    int enviadas = 0;
    var destinatarios = repositorio.destinatariosPendientes(puenteId, referencia.inspeccionId());
    while (!destinatarios.isEmpty()) {
      for (UUID usuario : destinatarios) {
        notificaciones.notificar(
            usuario,
            "INSPECCION_VENCIDA",
            "El puente "
                + referencia.codigo()
                + " ("
                + referencia.nombre()
                + ") lleva más de doce meses sin una inspección publicada. Revisa su ficha y gestiona una nueva evaluación.",
            "/puentes/" + puenteId);
        repositorio.registrar(puenteId, usuario, referencia);
        enviadas++;
      }
      destinatarios = repositorio.destinatariosPendientes(puenteId, referencia.inspeccionId());
    }
    return enviadas;
  }
}
