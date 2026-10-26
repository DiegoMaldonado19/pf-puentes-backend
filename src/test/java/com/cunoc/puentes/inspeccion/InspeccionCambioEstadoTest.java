package com.cunoc.puentes.inspeccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.indice.EstadoCondicion;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InspeccionCambioEstadoTest {

  private static final Instant HORA = Instant.parse("2026-10-24T18:00:00Z");
  private static final DatosTransicionInspeccion.Publicacion IC =
      new DatosTransicionInspeccion.Publicacion(
          new BigDecimal("80.00"),
          EstadoCondicion.BUENO,
          EstadoCondicion.BUENO,
          null,
          null,
          null,
          null);

  @Test
  void naceEnBorradorYElSetterPublicoDeEstadoNoExiste() {
    Inspeccion inspeccion = new Inspeccion();
    assertThat(inspeccion.getEstado()).isEqualTo(EstadoInspeccion.BORRADOR);
    assertThat(Arrays.stream(Inspeccion.class.getMethods()).map(Method::getName))
        .doesNotContain("setEstado");
  }

  @Test
  void enviarYPublicarRegistranLasFechasDelCicloYElIc() {
    Inspeccion inspeccion = new Inspeccion();
    inspeccion.cambiarEstado(
        EstadoInspeccion.ENVIADA, new DatosTransicionInspeccion(HORA, null, null, null));
    assertThat(inspeccion.getEstado()).isEqualTo(EstadoInspeccion.ENVIADA);
    assertThat(inspeccion.getEnviadaEn()).isEqualTo(HORA);

    inspeccion.cambiarEstado(
        EstadoInspeccion.PUBLICADA,
        new DatosTransicionInspeccion(HORA.plusSeconds(60), null, null, IC));
    assertThat(inspeccion.getEstado()).isEqualTo(EstadoInspeccion.PUBLICADA);
    assertThat(inspeccion.getPublicadaEn()).isEqualTo(HORA.plusSeconds(60));
    assertThat(inspeccion.getIndiceCondicion()).isEqualByComparingTo("80.00");
    assertThat(inspeccion.getEstadoCalculado()).isEqualTo(EstadoCondicion.BUENO);
  }

  @Test
  void publicarConIcIncompletoNoCambiaElEstadoNiLasFechas() {
    Inspeccion inspeccion = new Inspeccion();
    inspeccion.cambiarEstado(
        EstadoInspeccion.ENVIADA, new DatosTransicionInspeccion(HORA, null, null, null));
    DatosTransicionInspeccion.Publicacion incompleta =
        new DatosTransicionInspeccion.Publicacion(
            null, EstadoCondicion.BUENO, EstadoCondicion.BUENO, null, null, null, null);
    DatosTransicionInspeccion.Publicacion sinCalculado =
        new DatosTransicionInspeccion.Publicacion(
            new BigDecimal("80.00"), null, EstadoCondicion.BUENO, null, null, null, null);
    DatosTransicionInspeccion.Publicacion sinConfirmado =
        new DatosTransicionInspeccion.Publicacion(
            new BigDecimal("80.00"), EstadoCondicion.BUENO, null, null, null, null, null);
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                inspeccion.cambiarEstado(
                    EstadoInspeccion.PUBLICADA,
                    new DatosTransicionInspeccion(HORA, null, null, incompleta)));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                inspeccion.cambiarEstado(
                    EstadoInspeccion.PUBLICADA,
                    new DatosTransicionInspeccion(HORA, null, null, sinCalculado)));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                inspeccion.cambiarEstado(
                    EstadoInspeccion.PUBLICADA,
                    new DatosTransicionInspeccion(HORA, null, null, sinConfirmado)));
    assertThat(inspeccion.getEstado()).isEqualTo(EstadoInspeccion.ENVIADA);
  }

  @Test
  void publicarSinIcNoCambiaElEstadoNiLasFechas() {
    Inspeccion inspeccion = new Inspeccion();
    inspeccion.cambiarEstado(
        EstadoInspeccion.ENVIADA, new DatosTransicionInspeccion(HORA, null, null, null));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                inspeccion.cambiarEstado(
                    EstadoInspeccion.PUBLICADA,
                    new DatosTransicionInspeccion(HORA, null, null, null)));
    assertThat(inspeccion.getEstado()).isEqualTo(EstadoInspeccion.ENVIADA);
    assertThat(inspeccion.getPublicadaEn()).isNull();
    assertThat(inspeccion.getIndiceCondicion()).isNull();
  }

  @Test
  void unSaltoInvalidoNoDejaCambiosParciales() {
    Inspeccion inspeccion = new Inspeccion();
    assertThatThrownBy(
            () ->
                inspeccion.cambiarEstado(
                    EstadoInspeccion.PUBLICADA,
                    new DatosTransicionInspeccion(HORA, null, null, IC)))
        .isInstanceOf(NegocioException.class);
    assertThat(inspeccion.getEstado()).isEqualTo(EstadoInspeccion.BORRADOR);
    assertThat(inspeccion.getPublicadaEn()).isNull();
  }

  @Test
  void soloUnBorradorAdmiteCambiosDeDatos() {
    Inspeccion inspeccion = new Inspeccion();
    inspeccion.setDatos(Map.of("a", 1));
    inspeccion.exigirEditable();
    inspeccion.cambiarEstado(
        EstadoInspeccion.ENVIADA, new DatosTransicionInspeccion(HORA, null, null, null));
    assertThatThrownBy(inspeccion::exigirEditable)
        .isInstanceOfSatisfying(
            NegocioException.class,
            error -> {
              assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(error.getBody().getType().toString()).endsWith("inspeccion-no-editable");
            });
    assertThat(inspeccion.getDatos()).containsEntry("a", 1);
  }

  @Test
  void unaPublicadaEsInmutable() {
    Inspeccion inspeccion = new Inspeccion();
    inspeccion.cambiarEstado(
        EstadoInspeccion.ENVIADA, new DatosTransicionInspeccion(null, null, null, null));
    inspeccion.cambiarEstado(
        EstadoInspeccion.PUBLICADA, new DatosTransicionInspeccion(HORA, null, null, IC));
    assertThatThrownBy(inspeccion::exigirEditable).isInstanceOf(NegocioException.class);
    assertThat(inspeccion.getEstado()).isEqualTo(EstadoInspeccion.PUBLICADA);
  }

  @Test
  void elRetornoABorradorPermiteEditarDeNuevo() {
    Inspeccion inspeccion = new Inspeccion();
    inspeccion.cambiarEstado(
        EstadoInspeccion.ENVIADA, new DatosTransicionInspeccion(HORA, null, null, null));
    inspeccion.cambiarEstado(
        EstadoInspeccion.EN_REVISION, new DatosTransicionInspeccion(HORA, null, null, null));
    inspeccion.cambiarEstado(
        EstadoInspeccion.CAMBIOS_SOLICITADOS,
        new DatosTransicionInspeccion(HORA, "corregir estribo", null, null));
    inspeccion.cambiarEstado(
        EstadoInspeccion.BORRADOR, new DatosTransicionInspeccion(HORA, null, null, null));
    inspeccion.exigirEditable();
    assertThat(inspeccion.getEstado()).isEqualTo(EstadoInspeccion.BORRADOR);
  }
}
