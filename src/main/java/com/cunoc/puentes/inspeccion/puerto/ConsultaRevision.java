package com.cunoc.puentes.inspeccion.puerto;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que la inspección necesita saber de su revisión sin depender del paquete {@code revision} (ADR
 * 0057: el envío bloqueado por observaciones pendientes).
 */
public interface ConsultaRevision {

  /** Observaciones ancladas del último ciclo de revisión que siguen sin resolver. */
  int observacionesPendientes(UUID inspeccionId);

  /**
   * Resumen de la última revisión emitida de cada inspección, en una sola consulta para las listas.
   * Las inspecciones sin revisión emitida no aparecen en el mapa.
   */
  Map<UUID, ResumenRevision> resumenes(Collection<UUID> inspeccionIds);
}
