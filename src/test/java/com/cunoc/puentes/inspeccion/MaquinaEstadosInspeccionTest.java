package com.cunoc.puentes.inspeccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.indice.EstadoCondicion;
import java.math.BigDecimal;
import java.time.Instant;
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

class MaquinaEstadosInspeccionTest {

  private static final DatosTransicionInspeccion CON_MOTIVO =
      new DatosTransicionInspeccion(
          Instant.parse("2026-10-24T18:00:00Z"),
          "Hay que profundizar el estribo",
          null,
          new DatosTransicionInspeccion.Publicacion(
              new BigDecimal("80.00"),
              EstadoCondicion.BUENO,
              EstadoCondicion.BUENO,
              null,
              null,
              null,
              null));

  private final MaquinaEstadosInspeccion maquina = new MaquinaEstadosInspeccion();

  @ParameterizedTest(name = "{0} → {1}: habilitada={2}")
  @MethodSource("matrizDeEstados")
  void compruebaLos36ParesIncluidosTerminalesRepeticionesYSaltos(
      EstadoInspeccion actual, EstadoInspeccion destino, boolean habilitada) {
    if (habilitada) {
      assertThatNoException().isThrownBy(() -> maquina.validar(actual, destino, CON_MOTIVO));
    } else {
      comprobar(
          HttpStatus.CONFLICT,
          "transicion-invalida",
          () -> maquina.validar(actual, destino, CON_MOTIVO));
    }
  }

  static Stream<Arguments> matrizDeEstados() {
    Set<String> habilitadas =
        Set.of(
            "BORRADOR:ENVIADA",
            "ENVIADA:EN_REVISION",
            "ENVIADA:PUBLICADA",
            "EN_REVISION:PUBLICADA",
            "EN_REVISION:CAMBIOS_SOLICITADOS",
            "EN_REVISION:RECHAZADA",
            "CAMBIOS_SOLICITADOS:BORRADOR");
    return Arrays.stream(EstadoInspeccion.values())
        .flatMap(
            actual ->
                Arrays.stream(EstadoInspeccion.values())
                    .map(
                        destino ->
                            Arguments.of(
                                actual, destino, habilitadas.contains(actual + ":" + destino))));
  }

  @Test
  void exigeEstadosYDatosPresentes() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> maquina.validar(null, EstadoInspeccion.ENVIADA, CON_MOTIVO));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> maquina.validar(EstadoInspeccion.BORRADOR, null, CON_MOTIVO));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> maquina.validar(EstadoInspeccion.BORRADOR, EstadoInspeccion.ENVIADA, null));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void cambiosSolicitadosYRechazoExigenObservacion(String motivo) {
    DatosTransicionInspeccion datos =
        new DatosTransicionInspeccion(Instant.parse("2026-10-24T18:00:00Z"), motivo, null, null);
    comprobar(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "observacion-requerida",
        () ->
            maquina.validar(
                EstadoInspeccion.EN_REVISION, EstadoInspeccion.CAMBIOS_SOLICITADOS, datos));
    comprobar(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "observacion-requerida",
        () -> maquina.validar(EstadoInspeccion.EN_REVISION, EstadoInspeccion.RECHAZADA, datos));
  }

  @Test
  void publicarOEnviarNoExigenObservacion() {
    DatosTransicionInspeccion vacios =
        new DatosTransicionInspeccion(Instant.parse("2026-10-24T18:00:00Z"), null, null, null);
    assertThatNoException()
        .isThrownBy(
            () -> maquina.validar(EstadoInspeccion.BORRADOR, EstadoInspeccion.ENVIADA, vacios));
    assertThatNoException()
        .isThrownBy(
            () -> maquina.validar(EstadoInspeccion.ENVIADA, EstadoInspeccion.PUBLICADA, vacios));
  }

  @Test
  void enUsaLaHoraDelDispositivoCuandoViene() {
    Instant hora = Instant.parse("2026-10-25T16:05:00Z");
    DatosTransicionInspeccion datos = DatosTransicionInspeccion.en(hora);
    assertThat(datos.ocurridaEn()).isEqualTo(hora);
    assertThat(datos.motivo()).isNull();
    assertThat(datos.revisionId()).isNull();
    assertThat(datos.publicacion()).isNull();
  }

  @Test
  void enSinHoraUsaAhora() {
    Instant antes = Instant.now();
    DatosTransicionInspeccion datos = DatosTransicionInspeccion.en(null);
    Instant despues = Instant.now();
    assertThat(datos.ocurridaEn()).isBetween(antes, despues);
    assertThat(datos.motivo()).isNull();
    assertThat(datos.revisionId()).isNull();
    assertThat(datos.publicacion()).isNull();
  }

  private static void comprobar(HttpStatus estado, String tipo, Runnable operacion) {
    assertThatThrownBy(operacion::run)
        .isInstanceOfSatisfying(
            NegocioException.class,
            error -> {
              assertThat(error.getStatusCode()).isEqualTo(estado);
              assertThat(error.getBody().getType().toString())
                  .isEqualTo("https://sgp.cunoc.usac.edu.gt/errores/" + tipo);
              assertThat(error.getBody().getDetail()).isNotBlank();
            });
  }
}
