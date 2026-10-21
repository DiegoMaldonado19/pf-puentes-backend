package com.cunoc.puentes.common.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ContadorDeVentanaFijaTest {

  private static final Instant T0 = Instant.parse("2026-10-20T15:00:00Z");
  private static final Duration MINUTO = Duration.ofMinutes(1);
  private static final String CLAVE = "10.0.0.1 POST /login";

  private final ContadorDeVentanaFija contador = new ContadorDeVentanaFija();

  @Test
  void dentroDelLimitePasaYAlExcederloBloquea() {
    assertThat(contador.registrar(CLAVE, 2, MINUTO, T0).admitida()).isTrue();
    assertThat(contador.registrar(CLAVE, 2, MINUTO, T0.plusSeconds(10)).admitida()).isTrue();

    ContadorDeVentanaFija.Decision tercera =
        contador.registrar(CLAVE, 2, MINUTO, T0.plusSeconds(20));

    assertThat(tercera.admitida()).isFalse();
    assertThat(tercera.reintentoSegundos()).isEqualTo(40);
  }

  @Test
  void alVencerLaVentanaSeReinicia() {
    contador.registrar(CLAVE, 1, MINUTO, T0);
    assertThat(contador.registrar(CLAVE, 1, MINUTO, T0.plusSeconds(30)).admitida()).isFalse();
    assertThat(contador.registrar(CLAVE, 1, MINUTO, T0.plus(MINUTO)).admitida()).isTrue();
  }

  @Test
  void otraClaveNoComparteLaVentana() {
    contador.registrar(CLAVE, 1, MINUTO, T0);
    assertThat(contador.registrar("10.0.0.2 POST /login", 1, MINUTO, T0).admitida()).isTrue();
  }

  @Test
  void purgaLasVentanasVencidas() {
    contador.registrar(CLAVE, 1, MINUTO, T0);
    assertThat(contador.tamaño()).isEqualTo(1);

    contador.purgar(T0.plus(MINUTO));

    assertThat(contador.tamaño()).isZero();
  }
}
