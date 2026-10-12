package com.cunoc.puentes.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CalculadoraIndiceCondicionTest {

  private final CalculadoraIndiceCondicion calculadora = new CalculadoraIndiceCondicion();

  @Test
  void todosLosElementosSanosDanCienIndependientementeDeSusPesos() {
    assertThat(calculadora.calcular(List.of(elemento(0, "3"), elemento(0, "0.5"))))
        .isEqualByComparingTo("100");
  }

  @Test
  void todosLosElementosConDanoMaximoDanCero() {
    assertThat(calculadora.calcular(List.of(elemento(4, "3"), elemento(4, "0.5"))))
        .isEqualByComparingTo("0");
  }

  @ParameterizedTest
  @CsvSource({"0, 100", "1, 75", "2, 50", "3, 25", "4, 0"})
  void calculaLaEscalaParaUnSoloElemento(int calificacion, String esperado) {
    assertThat(calculadora.calcular(List.of(elemento(calificacion, "7.25"))))
        .isEqualByComparingTo(esperado);
  }

  @Test
  void calculaElEjemploPonderadoDelEnunciadoSinReducirloADosDecimales() {
    List<CalificacionElemento> elementos =
        List.of(elemento(2, "3"), elemento(0, "1"), elemento(4, "2"));

    assertThat(calculadora.calcular(elementos))
        .isEqualByComparingTo("41.66666666666666666666666666666667");
  }

  @Test
  void ponderaPorCriticidadEnLugarDePromediarLasCalificaciones() {
    assertThat(calculadora.calcular(List.of(elemento(0, "3"), elemento(4, "1"))))
        .isEqualByComparingTo("75");
    assertThat(calculadora.calcular(List.of(elemento(0, "1"), elemento(4, "3"))))
        .isEqualByComparingTo("25");
  }

  @Test
  void admitePesosDecimalesSinTruncarLosProductos() {
    assertThat(calculadora.calcular(List.of(elemento(1, "0.5"), elemento(3, "1.5"))))
        .isEqualByComparingTo("37.5");
  }

  @Test
  void laEscalaDeLosPesosNoCambiaElIndice() {
    BigDecimal original =
        calculadora.calcular(List.of(elemento(2, "3"), elemento(0, "1"), elemento(4, "2")));
    BigDecimal escalado =
        calculadora.calcular(
            List.of(elemento(2, "0.03"), elemento(0, "0.01"), elemento(4, "0.02")));

    assertThat(escalado).isEqualByComparingTo(original);
  }

  @Test
  void elOrdenDeLosElementosNoCambiaElIndice() {
    List<CalificacionElemento> elementos =
        List.of(elemento(2, "3"), elemento(0, "1"), elemento(4, "2"));

    assertThat(calculadora.calcular(elementos.reversed()))
        .isEqualByComparingTo(calculadora.calcular(elementos));
  }

  @Test
  void aumentarUnDanoReduceElIndiceSinSalirDeLaEscala() {
    BigDecimal anterior = calculadora.calcular(List.of(elemento(1, "3"), elemento(2, "1")));
    BigDecimal posterior = calculadora.calcular(List.of(elemento(2, "3"), elemento(2, "1")));

    assertThat(anterior).isEqualByComparingTo("68.75");
    assertThat(posterior).isEqualByComparingTo("50").isLessThan(anterior);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1E+1000", "1E-1000"})
  void calculaSinDesbordarNiConvertirPesosPequenosEnCero(String peso) {
    assertThat(calculadora.calcular(List.of(elemento(1, peso), elemento(3, peso))))
        .isEqualByComparingTo("50");
  }

  @ParameterizedTest
  @NullAndEmptySource
  void rechazaUnaEvaluacionAusenteOVacia(List<CalificacionElemento> elementos) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> calculadora.calcular(elementos))
        .withMessage("Se requiere al menos un elemento evaluado.");
  }

  @Test
  void rechazaUnElementoAusenteSinSaltarloNiAsignarleCero() {
    List<CalificacionElemento> elementos = Arrays.asList(elemento(0, "1"), null);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> calculadora.calcular(elementos))
        .withMessage("Los elementos evaluados no pueden ser nulos.");
  }

  private static CalificacionElemento elemento(int calificacion, String peso) {
    return new CalificacionElemento(calificacion, new BigDecimal(peso));
  }
}
