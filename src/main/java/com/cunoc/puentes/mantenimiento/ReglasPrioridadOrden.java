package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.common.NegocioException;
import org.springframework.http.HttpStatus;

/** RN-MTO-06: valida un ajuste manual; no calcula la prioridad sugerida ni autoriza actores. */
final class ReglasPrioridadOrden {

  void validar(Prioridad actual, Prioridad nueva, String justificacion) {
    if (actual == null) {
      throw new IllegalArgumentException("La orden debe tener una prioridad vigente.");
    }
    if (nueva == null) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "prioridad-de-orden-requerida",
          "Prioridad requerida",
          "Debe indicar la nueva prioridad de la orden.");
    }
    if (actual != nueva && (justificacion == null || justificacion.isBlank())) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "justificacion-de-prioridad-requerida",
          "Justificación requerida",
          "Debe justificar el cambio de prioridad de la orden.");
    }
  }
}
