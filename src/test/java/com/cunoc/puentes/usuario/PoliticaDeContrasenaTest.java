package com.cunoc.puentes.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PoliticaDeContrasenaTest {

  @Test
  void aceptaDiezCaracteresConLetraYDigito() {
    assertThat(PoliticaDeContrasena.cumple("abcdefghi1")).isTrue();
  }

  @Test
  void rechazaMenosDeDiezCaracteres() {
    assertThat(PoliticaDeContrasena.cumple("abcdefgh1")).isFalse();
  }

  @Test
  void rechazaSinDigito() {
    assertThat(PoliticaDeContrasena.cumple("abcdefghijk")).isFalse();
  }

  @Test
  void rechazaSinLetra() {
    assertThat(PoliticaDeContrasena.cumple("12345678901")).isFalse();
  }

  @Test
  void rechazaNulo() {
    assertThat(PoliticaDeContrasena.cumple(null)).isFalse();
  }
}
