package com.cunoc.puentes.indice;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/** Núcleo puro del IC (RN-IC-01/02/04, DT-ARQ-06): cálculo y clasificación sin anulaciones. */
public final class CalculadoraIndiceCondicion {

  private static final BigDecimal CIEN = BigDecimal.valueOf(100);
  private static final BigDecimal MINIMO_BUENO = BigDecimal.valueOf(75);
  private static final BigDecimal MINIMO_REGULAR = BigDecimal.valueOf(50);

  /** Clasifica el IC sin aplicar redondeo de presentación ni condiciones de anulación. */
  public EstadoCondicion clasificar(BigDecimal indice) {
    if (indice == null || indice.signum() < 0 || indice.compareTo(CIEN) > 0) {
      throw new IllegalArgumentException("El IC debe estar presente y estar entre 0 y 100.");
    }
    if (indice.compareTo(MINIMO_BUENO) >= 0) {
      return EstadoCondicion.BUENO;
    }
    if (indice.compareTo(MINIMO_REGULAR) >= 0) {
      return EstadoCondicion.REGULAR;
    }
    return EstadoCondicion.MALO;
  }

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
