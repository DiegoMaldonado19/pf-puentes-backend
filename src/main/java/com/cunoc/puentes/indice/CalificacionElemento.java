package com.cunoc.puentes.indice;

import java.math.BigDecimal;

/** Entrada normalizada de daño y criticidad de un elemento para RN-IC-02. */
public record CalificacionElemento(int calificacion, BigDecimal peso) {

  static final int CALIFICACION_MAXIMA = 4;

  public CalificacionElemento {
    if (calificacion < 0 || calificacion > CALIFICACION_MAXIMA) {
      throw new IllegalArgumentException("La calificación debe estar entre 0 y 4.");
    }
    if (peso == null || peso.signum() <= 0) {
      throw new IllegalArgumentException("El peso debe estar presente y ser positivo.");
    }
  }
}
