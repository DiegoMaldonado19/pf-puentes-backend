package com.cunoc.puentes.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** El cálculo de la ventana y del bloqueo de RN-USR-07, sin base de datos. */
class PoliticaDeBloqueoTest {

  private static final Instant AHORA = Instant.parse("2026-10-17T12:00:00Z");

  private final PoliticaDeBloqueo politica =
      new PoliticaDeBloqueo(5, Duration.ofMinutes(15), Duration.ofMinutes(15));

  @Test
  void laVentanaEmpiezaQuinceMinutosAntes() {
    assertThat(politica.inicioDeVentana(AHORA)).isEqualTo(AHORA.minusSeconds(15 * 60));
  }

  @Test
  void elBloqueoDuraQuinceMinutos() {
    assertThat(politica.bloqueadoHasta(AHORA)).isEqualTo(AHORA.plusSeconds(15 * 60));
  }

  @Test
  void exponeElMaximoDeIntentos() {
    assertThat(politica.maximo()).isEqualTo(5);
  }

  @Test
  void usaLosPlazosConfigurados() {
    PoliticaDeBloqueo otra = new PoliticaDeBloqueo(3, Duration.ofMinutes(5), Duration.ofHours(1));

    assertThat(otra.maximo()).isEqualTo(3);
    assertThat(otra.inicioDeVentana(AHORA)).isEqualTo(AHORA.minusSeconds(5 * 60));
    assertThat(otra.bloqueadoHasta(AHORA)).isEqualTo(AHORA.plusSeconds(60 * 60));
  }

  @Test
  void sinFechaONoEstaBloqueadaSiYaVencio() {
    assertThat(PoliticaDeBloqueo.estaBloqueada(null, AHORA)).isFalse();
    assertThat(PoliticaDeBloqueo.estaBloqueada(AHORA.minusSeconds(1), AHORA)).isFalse();
    assertThat(PoliticaDeBloqueo.estaBloqueada(AHORA, AHORA)).isFalse();
  }

  @Test
  void estaBloqueadaMientrasElFinEstaPorVenir() {
    assertThat(PoliticaDeBloqueo.estaBloqueada(AHORA.plusSeconds(1), AHORA)).isTrue();
  }

  @Test
  void losMinutosRestantesSeRedondeanHaciaArribaYNuncaSonCero() {
    assertThat(PoliticaDeBloqueo.minutosRestantes(AHORA.plusSeconds(15 * 60), AHORA)).isEqualTo(15);
    assertThat(PoliticaDeBloqueo.minutosRestantes(AHORA.plusSeconds(14 * 60 + 1), AHORA))
        .isEqualTo(15);
    assertThat(PoliticaDeBloqueo.minutosRestantes(AHORA.plusSeconds(61), AHORA)).isEqualTo(2);
    assertThat(PoliticaDeBloqueo.minutosRestantes(AHORA.plusSeconds(60), AHORA)).isEqualTo(1);
    assertThat(PoliticaDeBloqueo.minutosRestantes(AHORA.plusSeconds(1), AHORA)).isEqualTo(1);
  }
}
