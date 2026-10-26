package com.cunoc.puentes.indice;

import java.util.Objects;

/**
 * Modelo inmutable de confirmación del estado de condición por el evaluador (RN-IC-06).
 *
 * <p>Conserva tanto el estado calculado automáticamente como el estado confirmado. Si ambos
 * difieren, exige obligatoriamente una justificación no vacía por escrito.
 */
public record ConfirmacionEstadoCondicion(
    EstadoCondicion estadoCalculado,
    EstadoCondicion estadoConfirmado,
    String justificacionEstado,
    Anulacion anulacion) {

  public ConfirmacionEstadoCondicion {
    Objects.requireNonNull(estadoCalculado, "El estado calculado debe estar presente.");
    Objects.requireNonNull(estadoConfirmado, "El estado confirmado debe estar presente.");

    if (estadoCalculado != estadoConfirmado) {
      if (justificacionEstado == null || justificacionEstado.isBlank()) {
        throw new IllegalArgumentException(
            "Si el estado confirmado difiere del calculado, se requiere una justificación no vacía.");
      }
      justificacionEstado = justificacionEstado.strip();
    } else {
      justificacionEstado =
          (justificacionEstado != null && !justificacionEstado.isBlank())
              ? justificacionEstado.strip()
              : null;
    }
  }

  public static ConfirmacionEstadoCondicion de(
      EstadoCondicion estadoCalculado, EstadoCondicion estadoConfirmado, String justificacion) {
    return new ConfirmacionEstadoCondicion(estadoCalculado, estadoConfirmado, justificacion, null);
  }

  public static ConfirmacionEstadoCondicion de(
      EstadoCondicion estadoCalculado,
      EstadoCondicion estadoConfirmado,
      String justificacion,
      Anulacion anulacion) {
    return new ConfirmacionEstadoCondicion(
        estadoCalculado, estadoConfirmado, justificacion, anulacion);
  }

  /** Indica si el evaluador modificó el estado respecto al cálculo automático. */
  public boolean tieneDiscrepancia() {
    return estadoCalculado != estadoConfirmado;
  }

  /**
   * Indica si la confirmación eleva a Regular o Bueno un estado forzado a Malo por una anulación
   * crítica de RN-IC-05.
   */
  public boolean esElevacionDeAnulacionCritica() {
    return anulacion != null && estadoConfirmado != EstadoCondicion.MALO;
  }

  /** Devuelve el estado efectivo que asume el puente o la inspección. */
  public EstadoCondicion estadoEfectivo() {
    return estadoConfirmado;
  }
}
