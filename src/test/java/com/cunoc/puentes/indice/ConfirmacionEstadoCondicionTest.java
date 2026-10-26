package com.cunoc.puentes.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ConfirmacionEstadoCondicionTest {

  @Test
  @DisplayName("Rechaza estado calculado nulo")
  void rechazaEstadoCalculadoNulo() {
    assertThatThrownBy(() -> ConfirmacionEstadoCondicion.de(null, EstadoCondicion.BUENO, "motivo"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("El estado calculado debe estar presente");
  }

  @Test
  @DisplayName("Rechaza estado confirmado nulo")
  void rechazaEstadoConfirmadoNulo() {
    assertThatThrownBy(() -> ConfirmacionEstadoCondicion.de(EstadoCondicion.BUENO, null, "motivo"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("El estado confirmado debe estar presente");
  }

  @Test
  @DisplayName("Acepta confirmación coincidente sin justificación")
  void aceptaConfirmacionCoincidenteSinJustificacion() {
    var conf = ConfirmacionEstadoCondicion.de(EstadoCondicion.BUENO, EstadoCondicion.BUENO, null);

    assertThat(conf.tieneDiscrepancia()).isFalse();
    assertThat(conf.justificacionEstado()).isNull();
    assertThat(conf.estadoEfectivo()).isEqualTo(EstadoCondicion.BUENO);
    assertThat(conf.esElevacionDeAnulacionCritica()).isFalse();
  }

  @Test
  @DisplayName("Normaliza justificación en confirmación coincidente")
  void normalizaJustificacionEnConfirmacionCoincidente() {
    var conEspacios =
        ConfirmacionEstadoCondicion.de(
            EstadoCondicion.REGULAR, EstadoCondicion.REGULAR, "  Coincide plenamente  ");
    assertThat(conEspacios.justificacionEstado()).isEqualTo("Coincide plenamente");

    var enBlanco =
        ConfirmacionEstadoCondicion.de(EstadoCondicion.REGULAR, EstadoCondicion.REGULAR, "   ");
    assertThat(enBlanco.justificacionEstado()).isNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t", "\n"})
  @DisplayName("Rechaza justificación vacía o en blanco cuando los estados difieren (RN-IC-06)")
  void rechazaJustificacionVaciaCuandoDifieren(String justificacionInvalida) {
    assertThatThrownBy(
            () ->
                ConfirmacionEstadoCondicion.de(
                    EstadoCondicion.BUENO, EstadoCondicion.REGULAR, justificacionInvalida))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Si el estado confirmado difiere del calculado, se requiere una justificación no vacía");
  }

  @Test
  @DisplayName("Acepta y limpia justificación válida cuando los estados difieren")
  void aceptaYLimpiaJustificacionValida() {
    var conf =
        ConfirmacionEstadoCondicion.de(
            EstadoCondicion.BUENO,
            EstadoCondicion.REGULAR,
            "   Deterioro visible no ponderado en la calzada   ");

    assertThat(conf.tieneDiscrepancia()).isTrue();
    assertThat(conf.estadoCalculado()).isEqualTo(EstadoCondicion.BUENO);
    assertThat(conf.estadoConfirmado()).isEqualTo(EstadoCondicion.REGULAR);
    assertThat(conf.justificacionEstado())
        .isEqualTo("Deterioro visible no ponderado en la calzada");
    assertThat(conf.estadoEfectivo()).isEqualTo(EstadoCondicion.REGULAR);
  }

  @Test
  @DisplayName("Detecta elevación de una anulación crítica forzada a Malo")
  void detectaElevacionDeAnulacionCritica() {
    var elevacionBueno =
        ConfirmacionEstadoCondicion.de(
            EstadoCondicion.MALO,
            EstadoCondicion.BUENO,
            "Criterio evaluador experto",
            Anulacion.SOCAVACION);
    assertThat(elevacionBueno.esElevacionDeAnulacionCritica()).isTrue();

    var elevacionRegular =
        ConfirmacionEstadoCondicion.de(
            EstadoCondicion.MALO,
            EstadoCondicion.REGULAR,
            "Reparación en curso",
            Anulacion.PERDIDA_SECCION);
    assertThat(elevacionRegular.esElevacionDeAnulacionCritica()).isTrue();

    var mantieneMalo =
        ConfirmacionEstadoCondicion.de(
            EstadoCondicion.MALO, EstadoCondicion.MALO, null, Anulacion.ASENTAMIENTO);
    assertThat(mantieneMalo.esElevacionDeAnulacionCritica()).isFalse();

    var sinAnulacion =
        ConfirmacionEstadoCondicion.de(
            EstadoCondicion.REGULAR, EstadoCondicion.BUENO, "Justificación", null);
    assertThat(sinAnulacion.esElevacionDeAnulacionCritica()).isFalse();
  }
}

