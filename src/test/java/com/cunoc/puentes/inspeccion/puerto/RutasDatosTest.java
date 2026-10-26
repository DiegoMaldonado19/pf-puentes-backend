package com.cunoc.puentes.inspeccion.puerto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RutasDatosTest {

  @Test
  void extraeSoloLasRutasPedidasYFusionaSinPisarHermanos() {
    Map<String, Object> origen = new LinkedHashMap<>();
    RutasDatos.escribir(origen, "identificacion.tipologia", "losas");
    RutasDatos.escribir(origen, "identificacion.numero_tramos", 3);
    RutasDatos.escribir(origen, "subestructura.estribo_entrada.calificacion", 4);

    Map<String, Object> permanentes =
        RutasDatos.extraer(
            origen, Set.of("identificacion.tipologia", "identificacion.numero_tramos"));
    assertThat(RutasDatos.leer(permanentes, "identificacion.tipologia")).isEqualTo("losas");
    assertThat(RutasDatos.leer(permanentes, "subestructura.estribo_entrada.calificacion")).isNull();

    Map<String, Object> overlay = new LinkedHashMap<>();
    RutasDatos.escribir(overlay, "identificacion.tipologia", "arco");
    Map<String, Object> fusion = RutasDatos.fusionar(origen, overlay);
    assertThat(RutasDatos.leer(fusion, "identificacion.tipologia")).isEqualTo("arco");
    assertThat(RutasDatos.leer(fusion, "identificacion.numero_tramos")).isEqualTo(3);
    assertThat(RutasDatos.leer(fusion, "subestructura.estribo_entrada.calificacion")).isEqualTo(4);
  }
}
