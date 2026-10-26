package com.cunoc.puentes.inspeccion;

import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.usuario.Rol;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class VisibilidadInspeccionTest {

  private static final UUID AUTOR = UUID.fromString("0199c200-0000-7000-8000-000000000011");
  private static final UUID CATEDRATICO = UUID.fromString("0199c200-0000-7000-8000-000000000013");
  private static final UUID AJENO = UUID.fromString("0199c200-0000-7000-8000-000000000012");
  private static final UUID CURSO = UUID.fromString("0199c200-0000-7000-8000-000000000031");

  @ParameterizedTest
  @EnumSource(
      value = EstadoInspeccion.class,
      names = {"BORRADOR", "ENVIADA", "EN_REVISION", "CAMBIOS_SOLICITADOS", "RECHAZADA"})
  void autorYCatedraticoDelCursoVenNoPublicadas(EstadoInspeccion estado) {
    Inspeccion inspeccion = noPublicada(estado);
    assertThat(VisibilidadInspeccion.visible(inspeccion, AUTOR, Rol.ESTUDIANTE, CATEDRATICO))
        .isTrue();
    assertThat(VisibilidadInspeccion.visible(inspeccion, CATEDRATICO, Rol.CATEDRATICO, CATEDRATICO))
        .isTrue();
    assertThat(VisibilidadInspeccion.visible(inspeccion, AJENO, Rol.ESTUDIANTE, CATEDRATICO))
        .isFalse();
    assertThat(VisibilidadInspeccion.visible(inspeccion, AJENO, Rol.CATEDRATICO, CATEDRATICO))
        .isFalse();
    assertThat(
            VisibilidadInspeccion.visible(inspeccion, AJENO, Rol.PROFESIONAL_EXTERNO, CATEDRATICO))
        .isFalse();
    assertThat(VisibilidadInspeccion.visible(inspeccion, AJENO, Rol.ADMINISTRADOR, CATEDRATICO))
        .isFalse();
  }

  @ParameterizedTest
  @CsvSource({"ESTUDIANTE", "PROFESIONAL_EXTERNO", "CATEDRATICO", "ADMINISTRADOR"})
  void unaPublicadaLaVeCualquierCuentaActiva(Rol rol) {
    Inspeccion inspeccion = noPublicada(EstadoInspeccion.PUBLICADA);
    assertThat(VisibilidadInspeccion.visible(inspeccion, AJENO, rol, CATEDRATICO)).isTrue();
  }

  @Test
  void sinCursoSoloLaVeElAutorMientrasNoSePublique() {
    Inspeccion inspeccion = noPublicada(EstadoInspeccion.BORRADOR);
    inspeccion.setCursoId(null);
    assertThat(
            VisibilidadInspeccion.visible(inspeccion, AUTOR, Rol.PROFESIONAL_EXTERNO, CATEDRATICO))
        .isTrue();
    assertThat(VisibilidadInspeccion.visible(inspeccion, CATEDRATICO, Rol.CATEDRATICO, CATEDRATICO))
        .isFalse();
  }

  @Test
  void eliminadaONulaNoEsVisible() {
    Inspeccion inspeccion = noPublicada(EstadoInspeccion.BORRADOR);
    inspeccion.setEliminadoEn(Instant.parse("2026-10-01T00:00:00Z"));
    assertThat(VisibilidadInspeccion.visible(inspeccion, AUTOR, Rol.ESTUDIANTE, CATEDRATICO))
        .isFalse();
    assertThat(VisibilidadInspeccion.visible(null, AUTOR, Rol.ESTUDIANTE, CATEDRATICO)).isFalse();
  }

  private static Inspeccion noPublicada(EstadoInspeccion estado) {
    Inspeccion inspeccion = new Inspeccion();
    inspeccion.setAutorId(AUTOR);
    inspeccion.setCursoId(CURSO);
    inspeccion.setEstado(estado);
    return inspeccion;
  }
}
