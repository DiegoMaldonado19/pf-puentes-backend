package com.cunoc.puentes.inspeccion.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValidadorUuidV7Test {

  private final ValidadorUuidV7 validador = new ValidadorUuidV7();

  @Test
  void aceptaVersion7YNulo() {
    assertThat(validador.isValid(null, null)).isTrue();
    assertThat(validador.isValid(UUID.fromString("0199c200-0000-7000-8000-0000000000aa"), null))
        .isTrue();
  }

  @Test
  void rechazaUuidQueNoEsV7() {
    assertThat(validador.isValid(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), null))
        .isFalse();
    assertThat(validador.isValid(UUID.randomUUID(), null)).isFalse();
  }
}
