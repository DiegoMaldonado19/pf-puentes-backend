package com.cunoc.puentes.inspeccion.puerto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.indice.Anulacion;
import com.cunoc.puentes.indice.EstadoCondicion;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Sql("/inspeccion/consulta.sql")
@Sql(
    scripts = "/inspeccion/consulta-limpiar.sql",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class CalculoIndiceProvisionalTest extends PruebaIntegracion {

  private static final UUID FORMULARIO = UUID.fromString("0199c200-0000-7000-8000-000000000021");
  private static final UUID PESOS = UUID.fromString("0199c200-0000-7000-8000-000000000061");
  private static final String DATOS_COMPLETOS =
      """
      {"identificacion":{"tipologia":"losas","material_principal":"concreto",\
      "numero_tramos":2,"longitud_m":18.0,"ancho_calzada_m":6.0,"anio_construccion":1998},\
      "subestructura":{"estribo_entrada":{"calificacion":1,"fisuras":0,"humedad":0,\
      "desconchado":0,"asentamiento":0,"socavacion":0},"estribo_salida":{"calificacion":1}},\
      "superestructura":{"losa":{"calificacion":2}},\
      "accesos":{"entrada":{"calificacion":1}},"cauce":{"calificacion":2}}
      """;
  private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {};

  @Autowired CalculoIndice calculo;
  @Autowired JdbcClient jdbc;
  @Autowired JsonMapper json;

  @Test
  void exigeDatosYVersion() {
    assertThatThrownBy(() -> calculo.calcular(null, FORMULARIO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> calculo.calcular(Map.of(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void calculaConLosPesosVigentes() {
    CalculoIndice.ResultadoCalculo resultado = calculo.calcular(completos(), FORMULARIO);
    assertThat(resultado.indiceCondicion()).isEqualByComparingTo("65.00");
    assertThat(resultado.estadoCalculado()).isEqualTo(EstadoCondicion.REGULAR);
    assertThat(resultado.anulacion()).isNull();
    assertThat(resultado.versionPesosId()).isEqualTo(PESOS);
  }

  @Test
  void socavacionAnulaAMaloConservandoElIndice() {
    Map<String, Object> datos = completos();
    @SuppressWarnings("unchecked")
    Map<String, Object> estribo =
        (Map<String, Object>)
            ((Map<String, Object>) datos.get("subestructura")).get("estribo_entrada");
    estribo.put("socavacion", 2);
    CalculoIndice.ResultadoCalculo resultado = calculo.calcular(datos, FORMULARIO);
    assertThat(resultado.indiceCondicion()).isEqualByComparingTo("65.00");
    assertThat(resultado.estadoCalculado()).isEqualTo(EstadoCondicion.MALO);
    assertThat(resultado.anulacion()).isEqualTo(Anulacion.SOCAVACION);
  }

  @Test
  void sinPesosVigentesNoInventaUnIndice() {
    jdbc.sql("delete from peso_elemento where version_pesos_id = :id").param("id", PESOS).update();
    jdbc.sql("delete from version_pesos where id = :id").param("id", PESOS).update();
    assertThatThrownBy(() -> calculo.calcular(completos(), FORMULARIO))
        .isInstanceOf(NegocioException.class)
        .satisfies(error -> comprobar((NegocioException) error));
  }

  @Test
  void sinCalificacionNoCalcula() {
    assertThatThrownBy(() -> calculo.calcular(Map.of(), FORMULARIO))
        .isInstanceOf(NegocioException.class)
        .satisfies(error -> comprobar((NegocioException) error));
  }

  private Map<String, Object> completos() {
    return json.readValue(DATOS_COMPLETOS, MAPA);
  }

  private static void comprobar(NegocioException error) {
    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(error.getBody().getType().toString()).endsWith("/ic-no-calculable");
  }
}
