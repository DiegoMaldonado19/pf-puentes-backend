package com.cunoc.puentes.common.seguridad;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class LimitadorDeTasaTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "login=10/PT1M",
        "POST /login",
        "POST /login=0/PT1M",
        "POST /login=10/PT0S",
        "PASE /login=10/PT1M"
      })
  void unaReglaMalFormadaImpideArrancar(String regla) {
    assertThatThrownBy(() -> new LimitadorDeTasa(List.of(regla), Clock.systemUTC(), respuestas()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sgp.limite-tasa.reglas")
        .hasMessageContaining(regla);
  }

  @Test
  void ignoraDeclaracionesVacias() {
    new LimitadorDeTasa(List.of("", "  "), Clock.systemUTC(), respuestas());
  }

  private static RespuestasSeguridad respuestas() {
    return new RespuestasSeguridad(mock(JsonMapper.class));
  }
}
