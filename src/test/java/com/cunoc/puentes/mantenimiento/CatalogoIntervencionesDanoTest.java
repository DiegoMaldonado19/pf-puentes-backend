package com.cunoc.puentes.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogoIntervencionesDanoTest {

  private final CatalogoIntervencionesDano catalogo = new CatalogoIntervencionesDano();

  @Test
  @DisplayName("Calificaciones 0 y 1 no generan propuesta de mantenimiento por daño")
  void calificacionesBajasNoGeneranPropuesta() {
    assertThat(
            catalogo.evaluar(
                "subestructura.estribo_entrada.grietas",
                0,
                BigDecimal.ONE,
                new BigDecimal("80.00"),
                false))
        .isEmpty();

    assertThat(
            catalogo.evaluar(
                "subestructura.estribo_entrada.grietas",
                1,
                new BigDecimal("3.0"),
                new BigDecimal("40.00"),
                false))
        .isEmpty();
  }

  @Test
  @DisplayName("Calificación 2 genera propuesta PREVENTIVO con prioridad según IC y peso")
  void calificacionDosGeneraPreventivo() {
    // IC >= 75 y peso < 2.0 -> BAJA
    var baja =
        catalogo
            .evaluar(
                "elementos_no_estructurales.barandas.danos",
                2,
                new BigDecimal("1.0"),
                new BigDecimal("85.00"),
                false)
            .orElseThrow();
    assertThat(baja.tipo()).isEqualTo(TipoOrden.PREVENTIVO);
    assertThat(baja.prioridadSugerida()).isEqualTo(Prioridad.BAJA);
    assertThat(baja.calificacionDano()).isEqualTo(2);
    assertThat(baja.descripcion())
        .contains("Mantenimiento preventivo", "barandas de protección vial", "grado 2");

    // 50 <= IC < 75 -> MEDIA
    var mediaPorIc =
        catalogo
            .evaluar(
                "superestructura.juntas.deterioro",
                2,
                new BigDecimal("1.0"),
                new BigDecimal("65.00"),
                false)
            .orElseThrow();
    assertThat(mediaPorIc.tipo()).isEqualTo(TipoOrden.PREVENTIVO);
    assertThat(mediaPorIc.prioridadSugerida()).isEqualTo(Prioridad.MEDIA);

    // IC >= 75 pero peso >= 2.0 -> MEDIA
    var mediaPorPeso =
        catalogo
            .evaluar(
                "superestructura.vigas.deformacion",
                2,
                new BigDecimal("2.5"),
                new BigDecimal("80.00"),
                false)
            .orElseThrow();
    assertThat(mediaPorPeso.prioridadSugerida()).isEqualTo(Prioridad.MEDIA);

    // IC < 50 -> ALTA
    var altaPorIcMalo =
        catalogo
            .evaluar(
                "superestructura.losa.agrietamiento",
                2,
                new BigDecimal("1.0"),
                new BigDecimal("45.00"),
                false)
            .orElseThrow();
    assertThat(altaPorIcMalo.prioridadSugerida()).isEqualTo(Prioridad.ALTA);
  }

  @Test
  @DisplayName("Calificación 3 genera CORRECTIVO o EMERGENCIA si es anulación crítica")
  void calificacionTresGeneraCorrectivoOEmergencia() {
    // Sin anulación crítica, peso normal e IC >= 50 -> CORRECTIVO / MEDIA
    var correctivaMedia =
        catalogo
            .evaluar(
                "subestructura.pilas.grietas",
                3,
                new BigDecimal("1.5"),
                new BigDecimal("70.00"),
                false)
            .orElseThrow();
    assertThat(correctivaMedia.tipo()).isEqualTo(TipoOrden.CORRECTIVO);
    assertThat(correctivaMedia.prioridadSugerida()).isEqualTo(Prioridad.MEDIA);
    assertThat(correctivaMedia.descripcion()).contains("Reparación correctiva", "grado 3");

    // Sin anulación crítica pero peso >= 2.0 -> CORRECTIVO / ALTA
    var correctivaAlta =
        catalogo
            .evaluar(
                "subestructura.pilas.grietas",
                3,
                new BigDecimal("2.0"),
                new BigDecimal("80.00"),
                false)
            .orElseThrow();
    assertThat(correctivaAlta.tipo()).isEqualTo(TipoOrden.CORRECTIVO);
    assertThat(correctivaAlta.prioridadSugerida()).isEqualTo(Prioridad.ALTA);

    // Con anulación crítica activa -> EMERGENCIA / ALTA
    var emergenciaCritica =
        catalogo
            .evaluar(
                "subestructura.estribo_entrada.socavacion",
                3,
                new BigDecimal("1.0"),
                new BigDecimal("80.00"),
                true)
            .orElseThrow();
    assertThat(emergenciaCritica.tipo()).isEqualTo(TipoOrden.EMERGENCIA);
    assertThat(emergenciaCritica.prioridadSugerida()).isEqualTo(Prioridad.ALTA);
    assertThat(emergenciaCritica.descripcion()).contains("Intervención de emergencia");
  }

  @Test
  @DisplayName("Calificación 4 siempre genera EMERGENCIA con prioridad ALTA")
  void calificacionCuatroGeneraEmergenciaAlta() {
    var emergencia =
        catalogo
            .evaluar(
                "elemento.personalizado.dano",
                4,
                new BigDecimal("1.0"),
                new BigDecimal("90.00"),
                false)
            .orElseThrow();
    assertThat(emergencia.tipo()).isEqualTo(TipoOrden.EMERGENCIA);
    assertThat(emergencia.prioridadSugerida()).isEqualTo(Prioridad.ALTA);
    assertThat(emergencia.elementoRef()).isEqualTo("elemento.personalizado.dano");
    assertThat(emergencia.descripcion())
        .contains(
            "Intervención de emergencia en elemento.personalizado.dano (daño registrado grado 4).");
  }

  @Test
  @DisplayName(
      "Valida argumentos obligatorios y rangos del catálogo y record PropuestaIntervencion")
  void validaArgumentosObligatoriosYRangos() {
    assertThatThrownBy(() -> catalogo.evaluar(" ", 2, BigDecimal.ONE, new BigDecimal("80"), false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> catalogo.evaluar("ref.a", -1, BigDecimal.ONE, new BigDecimal("80"), false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> catalogo.evaluar("ref.a", 5, BigDecimal.ONE, new BigDecimal("80"), false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> catalogo.evaluar("ref.a", 2, BigDecimal.ZERO, new BigDecimal("80"), false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> catalogo.evaluar("ref.a", 2, BigDecimal.ONE, new BigDecimal("-1"), false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> catalogo.evaluar("ref.a", 2, BigDecimal.ONE, new BigDecimal("101"), false))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () -> new PropuestaIntervencion(null, TipoOrden.PREVENTIVO, Prioridad.BAJA, "desc", 2))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PropuestaIntervencion("ref", null, Prioridad.BAJA, "desc", 2))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new PropuestaIntervencion("ref", TipoOrden.PREVENTIVO, Prioridad.BAJA, " ", 2))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new PropuestaIntervencion("ref", TipoOrden.PREVENTIVO, Prioridad.BAJA, "desc", 5))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
