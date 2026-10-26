package com.cunoc.puentes.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DetectorCondicionesCriticasTest extends PruebaIntegracion {

  private static final UUID FORM_SIECA_V1 = UUID.fromString("0192b000-0000-7000-8000-000000000001");

  @Autowired private DetectorCondicionesCriticas detector;

  @Test
  @DisplayName("Calificaciones menores a 3 en V1.0.0 no activan anulaciones críticas")
  void calificacionesMenoresATresNoActivanAnulaciones() {
    Map<String, Object> datos =
        Map.of(
            "subestructura",
                Map.of(
                    "estribo_entrada_socavacion", 2,
                    "estribo_salida_socavacion", 2,
                    "pilas_socavacion", 2,
                    "asentamiento_estructural", 2),
            "superestructura",
                Map.of(
                    "vigas_perdida_seccion", 2,
                    "vigas_ausencia_elemento", 2));

    CondicionesCriticas resultado = detector.detectar(FORM_SIECA_V1, datos);

    assertThat(resultado.socavacionConCimentacionExpuestaEnEstriboOPila()).isFalse();
    assertThat(resultado.asentamientoDeEstructura()).isFalse();
    assertThat(resultado.portanteCortadoOConPerdidaDeSeccion()).isFalse();
    assertThat(resultado.ausenciaTotalDePortante()).isFalse();
    assertThat(detector.referenciasCriticasActivas(FORM_SIECA_V1, datos)).isEmpty();
  }

  @Test
  @DisplayName("Detecta las 4 anulaciones críticas desde los 6 campos marcados en SIECA V1.0.0")
  void detectaCuatroAnulacionesDesdeCamposSiecaV1() {
    Map<String, Object> datos = new HashMap<>();
    datos.put(
        "subestructura",
        Map.of(
            "estribo_entrada_socavacion", 3,
            "asentamiento_estructural", 4));
    datos.put(
        "superestructura",
        Map.of(
            "vigas_perdida_seccion", 3,
            "vigas_ausencia_elemento", 4));

    CondicionesCriticas resultado = detector.detectar(FORM_SIECA_V1, datos);

    assertThat(resultado.socavacionConCimentacionExpuestaEnEstriboOPila()).isTrue();
    assertThat(resultado.asentamientoDeEstructura()).isTrue();
    assertThat(resultado.portanteCortadoOConPerdidaDeSeccion()).isTrue();
    assertThat(resultado.ausenciaTotalDePortante()).isTrue();
    assertThat(detector.referenciasCriticasActivas(FORM_SIECA_V1, datos))
        .containsExactlyInAnyOrder(
            "subestructura.estribo_entrada.socavacion",
            "subestructura.cimentacion.asentamiento",
            "superestructura.vigas.perdida_seccion",
            "superestructura.vigas.ausencia_elemento");
  }

  @Test
  @DisplayName("Combina banderas detectadas y explícitas con disyunción lógica")
  void combinaBanderas() {
    CondicionesCriticas a = new CondicionesCriticas(true, false, false, false);
    CondicionesCriticas b = new CondicionesCriticas(false, false, true, false);

    CondicionesCriticas combinadas = DetectorCondicionesCriticas.combinar(a, b);
    assertThat(combinadas.socavacionConCimentacionExpuestaEnEstriboOPila()).isTrue();
    assertThat(combinadas.asentamientoDeEstructura()).isFalse();
    assertThat(combinadas.portanteCortadoOConPerdidaDeSeccion()).isTrue();
    assertThat(combinadas.ausenciaTotalDePortante()).isFalse();

    assertThat(DetectorCondicionesCriticas.combinar(null, null))
        .isEqualTo(new CondicionesCriticas(false, false, false, false));
    assertThat(DetectorCondicionesCriticas.combinar(a, null)).isEqualTo(a);
    assertThat(DetectorCondicionesCriticas.combinar(null, b)).isEqualTo(b);
  }

  @Test
  @DisplayName("Rechaza argumentos nulos al detectar condiciones críticas")
  void rechazaNulos() {
    assertThatThrownBy(() -> detector.detectar(null, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> detector.detectar(FORM_SIECA_V1, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> detector.detectarDesdeEsquema(null, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
