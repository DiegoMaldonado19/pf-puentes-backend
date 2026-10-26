package com.cunoc.puentes.inspeccion;

public enum EstadoInspeccion {
  BORRADOR,
  ENVIADA,
  EN_REVISION,
  CAMBIOS_SOLICITADOS,
  PUBLICADA,
  RECHAZADA;

  boolean permite(EstadoInspeccion destino) {
    return switch (this) {
      case BORRADOR -> destino == ENVIADA;
      case ENVIADA -> destino == EN_REVISION || destino == PUBLICADA;
      case EN_REVISION ->
          destino == PUBLICADA || destino == CAMBIOS_SOLICITADOS || destino == RECHAZADA;
      case CAMBIOS_SOLICITADOS -> destino == BORRADOR;
      case PUBLICADA, RECHAZADA -> false;
    };
  }
}
