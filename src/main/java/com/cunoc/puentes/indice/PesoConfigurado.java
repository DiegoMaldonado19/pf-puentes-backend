package com.cunoc.puentes.indice;

import java.math.BigDecimal;

/** Peso de configuración validado contra los límites de peso_elemento, sin redondeo implícito. */
public record PesoConfigurado(String elementoRef, BigDecimal peso) {

  private static final BigDecimal PESO_MAXIMO = new BigDecimal("999.999");

  public PesoConfigurado {
    if (elementoRef == null
        || elementoRef.isBlank()
        || elementoRef.codePointCount(0, elementoRef.length()) > 200) {
      throw new IllegalArgumentException(
          "La referencia del elemento es obligatoria y admite hasta 200 caracteres.");
    }
    if (peso == null || peso.signum() <= 0 || peso.compareTo(PESO_MAXIMO) > 0) {
      throw new IllegalArgumentException("El peso debe ser positivo y no superar 999.999.");
    }
    if (peso.stripTrailingZeros().scale() > 3) {
      throw new IllegalArgumentException(
          "El peso admite como máximo tres decimales significativos.");
    }
  }
}
