package com.cunoc.puentes.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PoliticaDeContrasenaTest {

  @Test
  void aceptaDiezCaracteresConLetraYDigito() {
    assertThat(PoliticaDeContrasena.cumple("abcdefghi1")).isTrue();
    assertThat(PoliticaDeContrasena.cumple("Puentes2026ok")).isTrue();
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

  @Test
  void rechazaLoQueBcryptTruncaria() {
    assertThat(PoliticaDeContrasena.cumple("a1" + "x".repeat(70))).isTrue();
    assertThat(PoliticaDeContrasena.cumple("a1" + "x".repeat(71))).isFalse();
    // 36 letras de 2 bytes = 72 bytes más el dígito: pasa de 72 aunque tenga pocos caracteres
    assertThat(PoliticaDeContrasena.cumple("1" + "ñ".repeat(36))).isFalse();
  }
}
