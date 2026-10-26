package com.cunoc.puentes.inspeccion;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.common.NegocioException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ValidacionFechaInspeccionTest {

  private final ValidacionFechaInspeccion validador =
      new ValidacionFechaInspeccion(
          Clock.fixed(Instant.parse("2026-10-25T18:00:00Z"), ZoneOffset.UTC));

  @Test
  void aceptaHoyEnGuatemalaYElAnioDeConstruccion() {
    assertThatCode(() -> validador.exigir(LocalDate.of(2026, 10, 25), 1998))
        .doesNotThrowAnyException();
    assertThatCode(() -> validador.exigir(LocalDate.of(1998, 1, 1), 1998))
        .doesNotThrowAnyException();
    assertThatCode(() -> validador.exigir(LocalDate.of(1997, 12, 31), null))
        .doesNotThrowAnyException();
  }

  @Test
  void rechazaFechaFuturaOAnteriorAlAnioDeConstruccion() {
    assertThatThrownBy(() -> validador.exigir(LocalDate.of(2026, 10, 26), 1998))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            error -> {
              NegocioException negocio = (NegocioException) error;
              assertThatStatus(negocio, HttpStatus.UNPROCESSABLE_CONTENT, "fecha_inspeccion");
            });
    assertThatThrownBy(() -> validador.exigir(LocalDate.of(1997, 12, 31), 1998))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            error -> {
              NegocioException negocio = (NegocioException) error;
              assertThatStatus(negocio, HttpStatus.UNPROCESSABLE_CONTENT, "fecha_inspeccion");
            });
  }

  private static void assertThatStatus(NegocioException error, HttpStatus estado, String campo) {
    org.assertj.core.api.Assertions.assertThat(error.getStatusCode()).isEqualTo(estado);
    org.assertj.core.api.Assertions.assertThat(error.getBody().getProperties().get("errores"))
        .isInstanceOf(List.class)
        .asList()
        .first()
        .extracting("campo")
        .isEqualTo(campo);
  }
}
