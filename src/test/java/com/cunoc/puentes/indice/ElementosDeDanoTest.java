package com.cunoc.puentes.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ElementosDeDanoTest {

  private static final UUID FORMULARIO = UUID.fromString("019a0018-0000-7000-8000-000000000001");

  @Test
  void conservaLaVersionYCopiaLasReferenciasSinInterpretarSuSintaxis() {
    Set<String> referencias = new HashSet<>(Set.of("elemento-de-prueba"));
    ElementosDeDano elementos = new ElementosDeDano(FORMULARIO, referencias);
    referencias.clear();

    assertThat(elementos.formularioVersionId()).isEqualTo(FORMULARIO);
    assertThat(elementos.referencias()).containsExactly("elemento-de-prueba");
    assertThatThrownBy(() -> elementos.referencias().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rechazaMetadatosSinVersion() {
    assertThatNullPointerException().isThrownBy(() -> new ElementosDeDano(null, Set.of()));
  }

  @Test
  void rechazaReferenciasAusentes() {
    assertThatNullPointerException().isThrownBy(() -> new ElementosDeDano(FORMULARIO, null));
  }
}
