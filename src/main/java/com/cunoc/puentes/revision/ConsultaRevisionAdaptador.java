package com.cunoc.puentes.revision;

import com.cunoc.puentes.inspeccion.puerto.ConsultaRevision;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementa el puerto que usa el envío para exigir que se resuelvan las observaciones (ADR 0057).
 */
@Component
class ConsultaRevisionAdaptador implements ConsultaRevision {

  private final ObservacionRevisionRepository observaciones;

  ConsultaRevisionAdaptador(ObservacionRevisionRepository observaciones) {
    this.observaciones = observaciones;
  }

  @Override
  public int observacionesPendientes(UUID inspeccionId) {
    return Math.toIntExact(observaciones.contarPendientes(inspeccionId));
  }
}
