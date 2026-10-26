package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.common.NegocioException;
import org.springframework.http.HttpStatus;

/** Reglas de transición del dominio; no autoriza actores ni consulta persistencia. */
final class MaquinaEstadosInspeccion {

  void validar(EstadoInspeccion actual, EstadoInspeccion destino, DatosTransicionInspeccion datos) {
    if (actual == null || destino == null || datos == null) {
      throw new IllegalArgumentException("Los estados y los datos de transición son obligatorios.");
    }
    if (!actual.permite(destino)) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "transicion-invalida",
          "Transición de inspección no permitida",
          "La transición solicitada no está habilitada para el estado actual de la inspección.");
    }
    if ((destino == EstadoInspeccion.CAMBIOS_SOLICITADOS || destino == EstadoInspeccion.RECHAZADA)
        && (datos.motivo() == null || datos.motivo().isBlank())) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "observacion-requerida",
          "Observación requerida",
          "El veredicto que no aprueba debe llevar una observación escrita.");
    }
  }
}
