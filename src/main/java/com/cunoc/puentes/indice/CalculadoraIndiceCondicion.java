package com.cunoc.puentes.indice;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/** Núcleo puro del IC (RN-IC-01/02, DT-ARQ-06): recibe solo elementos evaluados y sus pesos. */
public final class CalculadoraIndiceCondicion {

  private static final BigDecimal CIEN = BigDecimal.valueOf(100);

  public BigDecimal calcular(List<CalificacionElemento> elementos) {
    if (elementos == null || elementos.isEmpty()) {
      throw new IllegalArgumentException("Se requiere al menos un elemento evaluado.");
    }

    BigDecimal sumaPesos = BigDecimal.ZERO;
    BigDecimal sumaDanosPonderados = BigDecimal.ZERO;
    for (CalificacionElemento elemento : elementos) {
      if (elemento == null) {
        throw new IllegalArgumentException("Los elementos evaluados no pueden ser nulos.");
      }
      sumaPesos = sumaPesos.add(elemento.peso());
      sumaDanosPonderados =
          sumaDanosPonderados.add(
              elemento.peso().multiply(BigDecimal.valueOf(elemento.calificacion())));
    }

    BigDecimal danoMaximo =
        sumaPesos.multiply(BigDecimal.valueOf(CalificacionElemento.CALIFICACION_MAXIMA));
    // Forma equivalente de RN-IC-02: redondea solo la división final, no los sumandos.
    return danoMaximo
        .subtract(sumaDanosPonderados)
        .multiply(CIEN)
        .divide(danoMaximo, MathContext.DECIMAL128);
  }
}
