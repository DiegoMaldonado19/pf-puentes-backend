package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.common.NegocioException;
import org.springframework.http.HttpStatus;

/** Reglas de transición del dominio; no autoriza actores ni consulta persistencia. */
final class MaquinaEstadosOrden {

  void validar(EstadoOrden actual, EstadoOrden destino, DatosTransicionOrden datos) {
    if (actual == null || destino == null || datos == null) {
      throw new IllegalArgumentException("Los estados y los datos de transición son obligatorios.");
    }
    if (!actual.permite(destino)) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "transicion-orden-no-permitida",
          "Transición de orden no permitida",
          "La transición solicitada no está habilitada para el estado actual de la orden.");
    }
    if (destino == EstadoOrden.EJECUTADA) {
      validarCierre(datos);
    }
    if (destino == EstadoOrden.DESCARTADA && (datos.motivo() == null || datos.motivo().isBlank())) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "motivo-de-orden-requerido",
          "Motivo requerido",
          "Debe documentarse el motivo para descartar una orden.");
    }
  }

  private void validarCierre(DatosTransicionOrden datos) {
    if (datos.fechaEjecucion() == null
        || datos.responsable() == null
        || datos.responsable().isBlank()
        || datos.fotosGuardadas() == 0) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "cierre-de-orden-incompleto",
          "Cierre de orden incompleto",
          "Para ejecutar una orden se requieren fecha, responsable y una fotografía guardada.");
    }
    // Compatibilidad con responsable VARCHAR(150), sin cortar silenciosamente el nombre.
    if (datos.responsable().codePointCount(0, datos.responsable().length()) > 150) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "responsable-de-orden-invalido",
          "Responsable inválido",
          "El responsable de la ejecución admite como máximo 150 caracteres.");
    }
  }
}
