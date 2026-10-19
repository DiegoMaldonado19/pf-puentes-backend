package com.cunoc.puentes.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PesoConfiguradoTest {

  @ParameterizedTest
  @ValueSource(strings = {"0.001", "1", "1.2300", "999.999", "999.9990", "1E+2"})
  void admitePesosRepresentablesSinCambiarElValor(String valor) {
    BigDecimal peso = new BigDecimal(valor);
    PesoConfigurado configurado = new PesoConfigurado("tramos[0].viga", peso);

    assertThat(configurado.peso()).isSameAs(peso);
    assertThat(configurado.elementoRef()).isEqualTo("tramos[0].viga");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t\n"})
  void rechazaReferenciasAusentesOVacias(String referencia) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new PesoConfigurado(referencia, BigDecimal.ONE));
  }

  @Test
  void respetaElLimiteDeCaracteresSinInventarLaSintaxisDelFormulario() {
    String referencia = "a".repeat(200);
    assertThat(new PesoConfigurado(referencia, BigDecimal.ONE).elementoRef()).isEqualTo(referencia);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new PesoConfigurado("a".repeat(201), BigDecimal.ONE));
    assertThat(new PesoConfigurado("😀".repeat(200), BigDecimal.ONE).elementoRef())
        .isEqualTo("😀".repeat(200));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new PesoConfigurado("😀".repeat(201), BigDecimal.ONE));
  }

  @Test
  void rechazaUnPesoAusente() {
    assertThatIllegalArgumentException().isThrownBy(() -> new PesoConfigurado("elemento", null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-0.001", "1000", "999.9991"})
  void rechazaPesosFueraDelRangoPersistible(String valor) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new PesoConfigurado("elemento", new BigDecimal(valor)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0.0001", "1.2345", "1.0001"})
  void rechazaPrecisionQueRequeririaRedondearLaCalibracion(String valor) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new PesoConfigurado("elemento", new BigDecimal(valor)))
        .withMessage("El peso admite como máximo tres decimales significativos.");
  }
}
