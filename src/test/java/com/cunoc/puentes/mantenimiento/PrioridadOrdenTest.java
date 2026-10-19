package com.cunoc.puentes.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.common.NegocioException;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class PrioridadOrdenTest {

  @ParameterizedTest
  @CsvSource({"ALTA,MEDIA", "ALTA,BAJA", "MEDIA,ALTA", "MEDIA,BAJA", "BAJA,ALTA", "BAJA,MEDIA"})
  void cambiarPrioridadConservaSugerenciaEstadoYMotivo(Prioridad actual, Prioridad nueva) {
    OrdenMantenimiento orden = orden(actual);
    orden.setDescripcion("Intervención de prueba");
    orden.setMotivo("Motivo previo");

    orden.ajustarPrioridad(nueva, "Decisión técnica justificada");

    assertThat(orden.getPrioridad()).isEqualTo(nueva);
    assertThat(orden.getJustificacionPrioridad()).isEqualTo("Decisión técnica justificada");
    assertThat(orden.getPrioridadSugerida()).isEqualTo(Prioridad.MEDIA);
    assertThat(orden.getEstado()).isEqualTo(EstadoOrden.PROPUESTA);
    assertThat(orden.getDescripcion()).isEqualTo("Intervención de prueba");
    assertThat(orden.getMotivo()).isEqualTo("Motivo previo");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t\n", "\u2003"})
  void rechazaCambioSinJustificacionYConservaLosValoresPrevios(String justificacion) {
    OrdenMantenimiento orden = orden(Prioridad.ALTA);

    assertThatThrownBy(() -> orden.ajustarPrioridad(Prioridad.BAJA, justificacion))
        .isInstanceOfSatisfying(
            NegocioException.class,
            error -> {
              assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(error.getBody().getType().getPath())
                  .endsWith("/justificacion-de-prioridad-requerida");
            });
    comprobarSinCambios(orden, Prioridad.ALTA);
  }

  @Test
  void rechazaPrioridadNulaSinBorrarLaVigente() {
    OrdenMantenimiento orden = orden(Prioridad.ALTA);

    assertThatThrownBy(() -> orden.ajustarPrioridad(null, "Justificación presente"))
        .isInstanceOfSatisfying(
            NegocioException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
    comprobarSinCambios(orden, Prioridad.ALTA);
  }

  @ParameterizedTest
  @EnumSource(Prioridad.class)
  void repetirLaPrioridadNoBorraNiReemplazaLaJustificacion(Prioridad prioridad) {
    OrdenMantenimiento orden = orden(prioridad);

    orden.ajustarPrioridad(prioridad, null);
    comprobarSinCambios(orden, prioridad);
    orden.ajustarPrioridad(prioridad, "Texto distinto sin cambio de prioridad");
    comprobarSinCambios(orden, prioridad);
  }

  @Test
  void volverALaSugeridaTambienConservaLaRazonDelCambioReal() {
    OrdenMantenimiento orden = orden(Prioridad.ALTA);

    orden.ajustarPrioridad(Prioridad.MEDIA, "Se retoma la prioridad sugerida");

    assertThat(orden.getPrioridad()).isEqualTo(Prioridad.MEDIA);
    assertThat(orden.getPrioridadSugerida()).isEqualTo(Prioridad.MEDIA);
    assertThat(orden.getJustificacionPrioridad()).isEqualTo("Se retoma la prioridad sugerida");
  }

  @Test
  void ajustarNoInventaUnaSugerenciaCuandoNoExiste() {
    OrdenMantenimiento orden = orden(Prioridad.MEDIA);
    orden.setPrioridadSugerida(null);

    orden.ajustarPrioridad(Prioridad.ALTA, "Justificación manual sin sugerencia calculada");

    assertThat(orden.getPrioridad()).isEqualTo(Prioridad.ALTA);
    assertThat(orden.getPrioridadSugerida()).isNull();
    assertThat(orden.getJustificacionPrioridad())
        .isEqualTo("Justificación manual sin sugerencia calculada");
  }

  @Test
  void ajusteNoSustituyeLaInicializacionDeUnaOrden() {
    OrdenMantenimiento orden = new OrdenMantenimiento();

    assertThatIllegalArgumentException()
        .isThrownBy(() -> orden.ajustarPrioridad(Prioridad.ALTA, "Justificación"));
    assertThat(orden.getPrioridad()).isNull();
    assertThat(orden.getJustificacionPrioridad()).isNull();
  }

  @Test
  void lasPrioridadesYLaJustificacionNoExponenSettersPublicos() {
    assertThat(Arrays.stream(OrdenMantenimiento.class.getMethods()).map(Method::getName))
        .doesNotContain("setPrioridad", "setPrioridadSugerida", "setJustificacionPrioridad");
  }

  private static OrdenMantenimiento orden(Prioridad prioridad) {
    OrdenMantenimiento orden = new OrdenMantenimiento();
    orden.setPrioridadSugerida(Prioridad.MEDIA);
    orden.setPrioridad(prioridad);
    orden.setJustificacionPrioridad("Justificación previa");
    return orden;
  }

  private static void comprobarSinCambios(OrdenMantenimiento orden, Prioridad prioridad) {
    assertThat(orden.getPrioridad()).isEqualTo(prioridad);
    assertThat(orden.getPrioridadSugerida()).isEqualTo(Prioridad.MEDIA);
    assertThat(orden.getJustificacionPrioridad()).isEqualTo("Justificación previa");
  }
}
