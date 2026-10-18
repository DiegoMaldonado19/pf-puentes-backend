package com.cunoc.puentes.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CalificacionElementoTest {

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 4})
  void aceptaLaEscalaCompletaDeDano(int calificacion) {
    CalificacionElemento elemento = new CalificacionElemento(calificacion, new BigDecimal("0.5"));

    assertThat(elemento.calificacion()).isEqualTo(calificacion);
    assertThat(elemento.peso()).isEqualByComparingTo("0.5");
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 5, Integer.MIN_VALUE, Integer.MAX_VALUE})
  void rechazaCalificacionesFueraDeLaEscala(int calificacion) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CalificacionElemento(calificacion, BigDecimal.ONE))
        .withMessage("La calificación debe estar entre 0 y 4.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "0.000", "-1", "-0.001"})
  void rechazaPesosQueNoSonPositivos(String peso) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CalificacionElemento(0, new BigDecimal(peso)))
        .withMessage("El peso debe estar presente y ser positivo.");
  }

  @Test
  void rechazaUnPesoAusenteSinConsiderarSanoElElemento() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CalificacionElemento(0, null))
        .withMessage("El peso debe estar presente y ser positivo.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"0.001", "1", "999.999", "1E+1000", "1E-1000"})
  void conservaElPesoPositivoSinRedondearlo(String peso) {
    assertThat(new CalificacionElemento(2, new BigDecimal(peso)).peso()).isEqualByComparingTo(peso);
  }
}
