package com.cunoc.puentes.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.common.NegocioException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class MaquinaEstadosOrdenTest {

  private static final LocalDate FECHA = LocalDate.of(2026, 10, 19);
  private static final DatosTransicionOrden COMPLETOS =
      new DatosTransicionOrden(FECHA, "Responsable de prueba", 1, "Motivo documentado");

  private final MaquinaEstadosOrden maquina = new MaquinaEstadosOrden();

  @ParameterizedTest(name = "{0} → {1}: habilitada={2}")
  @MethodSource("matrizDeEstados")
  void compruebaLos36ParesIncluidosTerminalesRepeticionesYSaltos(
      EstadoOrden actual, EstadoOrden destino, boolean habilitada) {
    if (habilitada) {
      assertThatNoException().isThrownBy(() -> maquina.validar(actual, destino, COMPLETOS));
    } else {
      comprobarError(HttpStatus.CONFLICT, () -> maquina.validar(actual, destino, COMPLETOS));
    }
  }

  static Stream<Arguments> matrizDeEstados() {
    // Expectativas independientes del enum; cancelación adoptada el 25/10/2026.
    Set<String> habilitadas =
        Set.of(
            "PROPUESTA:PROGRAMADA",
            "PROPUESTA:DESCARTADA",
            "PROGRAMADA:EN_EJECUCION",
            "PROGRAMADA:CANCELADA",
            "EN_EJECUCION:EJECUTADA");
    return Arrays.stream(EstadoOrden.values())
        .flatMap(
            actual ->
                Arrays.stream(EstadoOrden.values())
                    .map(
                        destino ->
                            Arguments.of(
                                actual, destino, habilitadas.contains(actual + ":" + destino))));
  }

  @Test
  void exigeEstadosYDatosInternosPresentes() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> maquina.validar(null, EstadoOrden.PROGRAMADA, COMPLETOS));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> maquina.validar(EstadoOrden.PROPUESTA, null, COMPLETOS));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> maquina.validar(EstadoOrden.PROPUESTA, EstadoOrden.PROGRAMADA, null));
  }

  @Test
  void contadorNegativoNoEsUnaEvidenciaValida() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new DatosTransicionOrden(FECHA, "Responsable", -1, null));
  }

  @Test
  void noExigeDatosDeCierreParaProgramarOIniciar() {
    DatosTransicionOrden vacios = new DatosTransicionOrden(null, null, 0, null);
    assertThatNoException()
        .isThrownBy(() -> maquina.validar(EstadoOrden.PROPUESTA, EstadoOrden.PROGRAMADA, vacios));
    assertThatNoException()
        .isThrownBy(
            () -> maquina.validar(EstadoOrden.PROGRAMADA, EstadoOrden.EN_EJECUCION, vacios));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t\n", "\u2003"})
  void descartarExigeMotivoNoVacio(String motivo) {
    DatosTransicionOrden datos = new DatosTransicionOrden(null, null, 0, motivo);
    comprobarError(
        HttpStatus.UNPROCESSABLE_CONTENT,
        () -> maquina.validar(EstadoOrden.PROPUESTA, EstadoOrden.DESCARTADA, datos));
  }

  @Test
  void descartarConMotivoNoExigeFotografiaNiDatosDeEjecucion() {
    assertThatNoException()
        .isThrownBy(
            () ->
                maquina.validar(
                    EstadoOrden.PROPUESTA,
                    EstadoOrden.DESCARTADA,
                    new DatosTransicionOrden(null, null, 0, "No requiere intervención")));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t\n", "\u2003"})
  void cancelarExigeMotivoNoVacio(String motivo) {
    comprobarError(
        HttpStatus.UNPROCESSABLE_CONTENT,
        () ->
            maquina.validar(
                EstadoOrden.PROGRAMADA,
                EstadoOrden.CANCELADA,
                new DatosTransicionOrden(null, null, 0, motivo)));
  }

  @Test
  void cierreExigeFechaAunqueTengaResponsableYFoto() {
    comprobarCierreIncompleto(new DatosTransicionOrden(null, "Responsable", 1, null));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t\n", "\u2003"})
  void cierreExigeResponsableAunqueTengaFechaYFoto(String responsable) {
    comprobarCierreIncompleto(new DatosTransicionOrden(FECHA, responsable, 1, null));
  }

  @Test
  void unaFotoNoGuardadaNoPermiteCerrar() {
    comprobarCierreIncompleto(new DatosTransicionOrden(FECHA, "Responsable", 0, null));
  }

  @Test
  void noTruncaResponsablesQueExcedanLaColumnaExistente() {
    comprobarCierreIncompleto(new DatosTransicionOrden(FECHA, "a".repeat(151), 1, null));
  }

  @Test
  void limiteDelResponsableCuentaCaracteresUnicodeYNoUnidadesUtf16() {
    String responsable = "\uD83D\uDE00".repeat(150);
    assertThatNoException()
        .isThrownBy(
            () ->
                maquina.validar(
                    EstadoOrden.EN_EJECUCION,
                    EstadoOrden.EJECUTADA,
                    new DatosTransicionOrden(FECHA, responsable, 2, null)));
  }

  private void comprobarCierreIncompleto(DatosTransicionOrden datos) {
    comprobarError(
        HttpStatus.UNPROCESSABLE_CONTENT,
        () -> maquina.validar(EstadoOrden.EN_EJECUCION, EstadoOrden.EJECUTADA, datos));
  }

  private static void comprobarError(HttpStatus esperado, Runnable operacion) {
    assertThatThrownBy(operacion::run)
        .isInstanceOfSatisfying(
            NegocioException.class,
            error -> {
              assertThat(error.getStatusCode()).isEqualTo(esperado);
              assertThat(error.getBody().getType().toString())
                  .startsWith("https://sgp.cunoc.usac.edu.gt/errores/");
              assertThat(error.getBody().getDetail()).isNotBlank();
            });
  }
}
