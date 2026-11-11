package com.cunoc.puentes.inspeccion.puerto;

import java.util.UUID;

/**
 * Lo que la inspección necesita saber de su revisión sin depender del paquete {@code revision} (ADR
 * 0057: el envío bloqueado por observaciones pendientes).
 */
public interface ConsultaRevision {

  /** Observaciones ancladas del último ciclo de revisión que siguen sin resolver. */
  int observacionesPendientes(UUID inspeccionId);
}
