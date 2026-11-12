package com.cunoc.puentes.inspeccion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatosTransicionInspeccionTest {

  @Test
  void unMotivoDeMasDe500CaracteresSeRecortaSinPartirUnCodigoDeCaracter() {
    // 600 letras acentuadas: el corte por unidades UTF-16 partiría caracteres de 2 bytes
    String observacion = "ó".repeat(600);

    String motivo = DatosTransicionInspeccion.de(observacion, null).motivo();

    assertThat(motivo.codePointCount(0, motivo.length())).isEqualTo(500);
    assertThat(motivo).isEqualTo("ó".repeat(500));
  }

  @Test
  void unMotivoDeEmojisNoQuedaConUnaMitadSuelta() {
    // Cada emoji son dos unidades UTF-16: 600 emojis son 600 puntos de código, 1200 unidades
    String observacion = "😀".repeat(600);

    String motivo = DatosTransicionInspeccion.de(observacion, null).motivo();

    assertThat(motivo.codePointCount(0, motivo.length())).isEqualTo(500);
    assertThat(motivo).isEqualTo("😀".repeat(500));
  }

  @Test
  void unMotivoCortoOUnoNuloPasanSinCambios() {
    assertThat(DatosTransicionInspeccion.de("Falta el croquis.", null).motivo())
        .isEqualTo("Falta el croquis.");
    assertThat(DatosTransicionInspeccion.de(null, null).motivo()).isNull();
  }
}
