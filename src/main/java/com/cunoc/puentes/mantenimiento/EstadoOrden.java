package com.cunoc.puentes.mantenimiento;

public enum EstadoOrden {
  PROPUESTA,
  PROGRAMADA,
  EN_EJECUCION,
  EJECUTADA,
  DESCARTADA,
  CANCELADA;

  // El origen de CANCELADA sigue pendiente de acuerdo; no se habilita por el diagrama secundario.
  boolean permite(EstadoOrden destino) {
    return switch (this) {
      case PROPUESTA -> destino == PROGRAMADA || destino == DESCARTADA;
      case PROGRAMADA -> destino == EN_EJECUCION;
      case EN_EJECUCION -> destino == EJECUTADA;
      case EJECUTADA, DESCARTADA, CANCELADA -> false;
    };
  }
}
