package com.cunoc.puentes.mantenimiento;

public enum EstadoOrden {
  PROPUESTA,
  PROGRAMADA,
  EN_EJECUCION,
  EJECUTADA,
  DESCARTADA,
  CANCELADA;

  // Cancelación desde PROGRAMADA: política adoptada por el responsable P8 el 25/10/2026.
  boolean permite(EstadoOrden destino) {
    return switch (this) {
      case PROPUESTA -> destino == PROGRAMADA || destino == DESCARTADA;
      case PROGRAMADA -> destino == EN_EJECUCION || destino == CANCELADA;
      case EN_EJECUCION -> destino == EJECUTADA;
      case EJECUTADA, DESCARTADA, CANCELADA -> false;
    };
  }
}
