package com.cunoc.puentes.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class AnulacionesIndiceCondicionTest {

  private final CalculadoraIndiceCondicion calculadora = new CalculadoraIndiceCondicion();

  @ParameterizedTest
  @MethodSource("combinacionesCriticas")
  void evaluaLasDieciseisCombinacionesConservandoTodasLasCausasYElIc(
      CondicionesCriticas condiciones, Set<Anulacion> esperadas) {
    List<CalificacionElemento> sanos = List.of(new CalificacionElemento(0, BigDecimal.ONE));

    ResultadoIndiceCondicion resultado = calculadora.calcular(sanos, condiciones);

    assertThat(resultado.indiceCondicion()).isEqualByComparingTo("100");
    assertThat(resultado.estadoCalculado())
        .isEqualTo(esperadas.isEmpty() ? EstadoCondicion.BUENO : EstadoCondicion.MALO);
    assertThat(resultado.anulaciones()).containsExactlyInAnyOrderElementsOf(esperadas);
    assertThat(condiciones.anulaciones()).containsExactlyInAnyOrderElementsOf(esperadas);
  }

  @ParameterizedTest
  @EnumSource(Anulacion.class)
  void cadaCausaFuerzaMaloInclusoConIcNoventa(Anulacion causa) {
    CondicionesCriticas condiciones =
        new CondicionesCriticas(
            causa == Anulacion.SOCAVACION,
            causa == Anulacion.ASENTAMIENTO,
            causa == Anulacion.PERDIDA_SECCION,
            causa == Anulacion.AUSENCIA_PORTANTE);
    List<CalificacionElemento> elementos =
        List.of(
            new CalificacionElemento(0, new BigDecimal("9")),
            new CalificacionElemento(4, BigDecimal.ONE));

    ResultadoIndiceCondicion resultado = calculadora.calcular(elementos, condiciones);

    assertThat(resultado.indiceCondicion()).isEqualByComparingTo("90");
    assertThat(resultado.estadoCalculado()).isEqualTo(EstadoCondicion.MALO);
    assertThat(resultado.anulaciones()).containsExactly(causa);
  }

  @ParameterizedTest
  @CsvSource({"0, BUENO", "1, BUENO", "2, REGULAR", "3, MALO", "4, MALO"})
  void sinCondicionesCriticasConservaLaClasificacionNormal(int dano, EstadoCondicion esperado) {
    ResultadoIndiceCondicion resultado =
        calculadora.calcular(
            List.of(new CalificacionElemento(dano, BigDecimal.ONE)),
            new CondicionesCriticas(false, false, false, false));

    assertThat(resultado.estadoCalculado()).isEqualTo(esperado);
    assertThat(resultado.indiceCondicion())
        .isEqualByComparingTo(BigDecimal.valueOf(100 - 25 * dano));
    assertThat(resultado.anulaciones()).isEmpty();
  }

  @Test
  void conservaLaPrecisionDeLaFormulaYLasCausasAunqueElIcYaSeaMalo() {
    List<CalificacionElemento> elementos =
        List.of(
            new CalificacionElemento(2, new BigDecimal("3")),
            new CalificacionElemento(0, BigDecimal.ONE),
            new CalificacionElemento(4, new BigDecimal("2")));

    ResultadoIndiceCondicion resultado =
        calculadora.calcular(elementos, new CondicionesCriticas(true, true, true, true));

    assertThat(resultado.indiceCondicion())
        .isEqualByComparingTo("41.66666666666666666666666666666667")
        .isEqualByComparingTo(calculadora.calcular(elementos));
    assertThat(resultado.estadoCalculado()).isEqualTo(EstadoCondicion.MALO);
    assertThat(resultado.anulaciones()).containsExactlyInAnyOrder(Anulacion.values());
  }

  @Test
  void rechazaCondicionesAusentesSinSuponerQueNoHayFallasCriticas() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> calculadora.calcular(List.of(new CalificacionElemento(0, BigDecimal.ONE)), null))
        .withMessage("Las condiciones críticas deben estar presentes.");
  }

  @Test
  void unaAnulacionNoPermiteOmitirLaValidacionDeLosDanos() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> calculadora.calcular(List.of(), new CondicionesCriticas(true, true, true, true)))
        .withMessage("Se requiere al menos un elemento evaluado.");
  }

  @Test
  void lasCausasDetectadasNoSePuedenModificar() {
    Set<Anulacion> causas = new CondicionesCriticas(true, false, false, false).anulaciones();

    assertThatThrownBy(() -> causas.add(Anulacion.ASENTAMIENTO))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(causas).containsExactly(Anulacion.SOCAVACION);
  }

  private static Stream<Arguments> combinacionesCriticas() {
    return Stream.of(
        arguments(new CondicionesCriticas(false, false, false, false), Set.of()),
        arguments(new CondicionesCriticas(true, false, false, false), Set.of(Anulacion.SOCAVACION)),
        arguments(
            new CondicionesCriticas(false, true, false, false), Set.of(Anulacion.ASENTAMIENTO)),
        arguments(
            new CondicionesCriticas(false, false, true, false), Set.of(Anulacion.PERDIDA_SECCION)),
        arguments(
            new CondicionesCriticas(false, false, false, true),
            Set.of(Anulacion.AUSENCIA_PORTANTE)),
        arguments(
            new CondicionesCriticas(true, true, false, false),
            Set.of(Anulacion.SOCAVACION, Anulacion.ASENTAMIENTO)),
        arguments(
            new CondicionesCriticas(true, false, true, false),
            Set.of(Anulacion.SOCAVACION, Anulacion.PERDIDA_SECCION)),
        arguments(
            new CondicionesCriticas(true, false, false, true),
            Set.of(Anulacion.SOCAVACION, Anulacion.AUSENCIA_PORTANTE)),
        arguments(
            new CondicionesCriticas(false, true, true, false),
            Set.of(Anulacion.ASENTAMIENTO, Anulacion.PERDIDA_SECCION)),
        arguments(
            new CondicionesCriticas(false, true, false, true),
            Set.of(Anulacion.ASENTAMIENTO, Anulacion.AUSENCIA_PORTANTE)),
        arguments(
            new CondicionesCriticas(false, false, true, true),
            Set.of(Anulacion.PERDIDA_SECCION, Anulacion.AUSENCIA_PORTANTE)),
        arguments(
            new CondicionesCriticas(true, true, true, false),
            Set.of(Anulacion.SOCAVACION, Anulacion.ASENTAMIENTO, Anulacion.PERDIDA_SECCION)),
        arguments(
            new CondicionesCriticas(true, true, false, true),
            Set.of(Anulacion.SOCAVACION, Anulacion.ASENTAMIENTO, Anulacion.AUSENCIA_PORTANTE)),
        arguments(
            new CondicionesCriticas(true, false, true, true),
            Set.of(Anulacion.SOCAVACION, Anulacion.PERDIDA_SECCION, Anulacion.AUSENCIA_PORTANTE)),
        arguments(
            new CondicionesCriticas(false, true, true, true),
            Set.of(Anulacion.ASENTAMIENTO, Anulacion.PERDIDA_SECCION, Anulacion.AUSENCIA_PORTANTE)),
        arguments(new CondicionesCriticas(true, true, true, true), Set.of(Anulacion.values())));
  }
}
