package com.cunoc.puentes.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResultadoIndiceCondicionTest {

  @Test
  void conservaElIndiceSinRedondearYCopiaLasCausasParaEvitarMutaciones() {
    BigDecimal indice = new BigDecimal("90.000");
    Set<Anulacion> causas = EnumSet.of(Anulacion.SOCAVACION);
    ResultadoIndiceCondicion resultado =
        new ResultadoIndiceCondicion(indice, EstadoCondicion.MALO, causas);

    causas.clear();

    assertThat(resultado.indiceCondicion()).isSameAs(indice);
    assertThat(resultado.estadoCalculado()).isEqualTo(EstadoCondicion.MALO);
    assertThat(resultado.anulaciones()).containsExactly(Anulacion.SOCAVACION);
    assertThatThrownBy(() -> resultado.anulaciones().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rechazaUnResultadoSinIndice() {
    assertThatNullPointerException()
        .isThrownBy(() -> new ResultadoIndiceCondicion(null, EstadoCondicion.BUENO, Set.of()))
        .withMessage("El IC del resultado no puede ser nulo.");
  }

  @Test
  void rechazaUnResultadoSinEstado() {
    assertThatNullPointerException()
        .isThrownBy(() -> new ResultadoIndiceCondicion(BigDecimal.ONE, null, Set.of()))
        .withMessage("El estado calculado no puede ser nulo.");
  }

  @Test
  void rechazaUnConjuntoDeCausasAusente() {
    assertThatNullPointerException()
        .isThrownBy(() -> new ResultadoIndiceCondicion(BigDecimal.ONE, EstadoCondicion.MALO, null));
  }

  @Test
  void rechazaUnaCausaNula() {
    Set<Anulacion> causas = new HashSet<>();
    causas.add(null);

    assertThatNullPointerException()
        .isThrownBy(
            () -> new ResultadoIndiceCondicion(BigDecimal.ONE, EstadoCondicion.MALO, causas));
  }
}
