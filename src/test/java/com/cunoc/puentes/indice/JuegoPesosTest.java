package com.cunoc.puentes.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class JuegoPesosTest {

  private static final UUID FORMULARIO = UUID.fromString("019a0000-0000-7000-8000-000000000001");

  @Test
  void identificaUnJuegoProvisionalSinActa() {
    JuegoPesos juego = new JuegoPesos(FORMULARIO, null, List.of(peso("elemento", "1")));

    assertThat(juego.formularioVersionId()).isEqualTo(FORMULARIO);
    assertThat(juego.acta()).isNull();
    assertThat(juego.esProvisional()).isTrue();
  }

  @Test
  void conservaLaReferenciaAlActaSinAfirmarQueHaVerificadoUnaFirma() {
    String acta = "actas/calibracion.pdf";
    JuegoPesos juego = new JuegoPesos(FORMULARIO, acta, List.of(peso("elemento", "1")));

    assertThat(juego.acta()).isEqualTo(acta);
    assertThat(juego.esProvisional()).isFalse();
  }

  @Test
  void rechazaUnFormularioAusente() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JuegoPesos(null, null, List.of(peso("elemento", "1"))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t\n"})
  void unActaVaciaNoSePresentaComoRespaldoAcademico(String acta) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JuegoPesos(FORMULARIO, acta, List.of(peso("elemento", "1"))));
  }

  @Test
  void respetaElLimiteDeLaReferenciaAlActa() {
    assertThat(new JuegoPesos(FORMULARIO, "a".repeat(300), List.of(peso("elemento", "1"))).acta())
        .hasSize(300);
    assertThat(new JuegoPesos(FORMULARIO, "😀".repeat(300), List.of(peso("elemento", "1"))).acta())
        .isEqualTo("😀".repeat(300));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new JuegoPesos(FORMULARIO, "a".repeat(301), List.of(peso("elemento", "1"))));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void rechazaUnJuegoAusenteOVacio(List<PesoConfigurado> pesos) {
    assertThatIllegalArgumentException().isThrownBy(() -> new JuegoPesos(FORMULARIO, null, pesos));
  }

  @Test
  void rechazaUnPesoNuloEnElJuego() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new JuegoPesos(FORMULARIO, null, Arrays.asList(peso("elemento", "1"), null)));
  }

  @Test
  void rechazaUnaReferenciaDuplicadaAunqueLosPesosSeanDiferentes() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new JuegoPesos(
                    FORMULARIO, null, List.of(peso("elemento", "1"), peso("elemento", "2"))))
        .withMessage("El juego no puede repetir referencias de elementos.");
  }

  @Test
  void losJuegosNuevosNoModificanLasDefinicionesAnteriores() {
    List<PesoConfigurado> entrada = new ArrayList<>(List.of(peso("a", "1"), peso("b", "2")));
    JuegoPesos anterior = new JuegoPesos(FORMULARIO, null, entrada);
    entrada.set(0, peso("a", "3"));
    JuegoPesos nuevo = new JuegoPesos(FORMULARIO, null, entrada);

    assertThat(anterior.pesos().getFirst().peso()).isEqualByComparingTo("1");
    assertThat(nuevo.pesos().getFirst().peso()).isEqualByComparingTo("3");
    assertThatThrownBy(() -> anterior.pesos().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(anterior.pesos()).hasSize(2);
  }

  private static PesoConfigurado peso(String referencia, String valor) {
    return new PesoConfigurado(referencia, new BigDecimal(valor));
  }
}
