package com.cunoc.puentes.formulario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.ErrorCampo;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.inspeccion.puerto.ConsultaFormulario;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Contra el SIECA V1.0.0 que carga Flyway: sus rutas, grupos y reglas salen de su esquema. */
@SpringBootTest(properties = "sgp.pruebas.formulario-real=true")
class ConsultaFormularioEsquemaTest extends PruebaIntegracion {

  private static final UUID SIECA = UUID.fromString("0192b000-0000-7000-8000-000000000001");

  @Autowired ConsultaFormulario consulta;
  @Autowired FormularioVersionRepository versiones;
  @Autowired FormularioValidacionService validacion;

  @Test
  void leeLosDanosYLosPermanentesDelEsquema() {
    assertThat(consulta.rutasDeDano(SIECA))
        .hasSize(19)
        .contains("subestructura.estribo_entrada_grietas")
        .doesNotContain("identificacion.codigo");
    assertThat(consulta.rutasPermanentes(SIECA))
        .contains("identificacion.codigo", "datos_generales.numero_tramos")
        .doesNotContain("subestructura.estribo_entrada_grietas");
  }

  @Test
  void agrupaLosDanosPorXGrupo() {
    assertThat(consulta.grupos(SIECA))
        .filteredOn(grupo -> grupo.id().equals("estribos"))
        .singleElement()
        .satisfies(grupo -> assertThat(grupo.rutasDano()).hasSize(4));
  }

  @Test
  void validarCompletoMarcaCadaCampoObligatorioAusente() {
    assertThatThrownBy(() -> consulta.validarCompleto(Map.of(), SIECA))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            error ->
                assertThat(rutas(error))
                    .contains(
                        "identificacion.codigo",
                        "subestructura.estribo_entrada_grietas",
                        "caracteristicas_cauce.proteccion_margenes"));
  }

  @Test
  void validarCompletoMarcaElObligatorioDeCadaTramo() {
    Map<String, Object> datos = Map.of("tramos", List.of(Map.of("numero_tramo", 1)));

    assertThatThrownBy(() -> consulta.validarCompleto(datos, SIECA))
        .isInstanceOf(NegocioException.class)
        .satisfies(error -> assertThat(rutas(error)).contains("tramos[0].longitud"));
  }

  @Test
  void validarParcialNoExigeCampos() {
    assertThatCode(() -> consulta.validarParcial(Map.of("tramos", List.of()), SIECA))
        .doesNotThrowAnyException();
  }

  @Test
  void validarParcialTrataLosNulosComoAusentes() {
    Map<String, Object> datos = new HashMap<>();
    datos.put("caracteristicas_cauce", null);

    assertThatCode(() -> consulta.validarParcial(datos, SIECA)).doesNotThrowAnyException();
  }

  @Test
  void validarParcialRechazaUnDanoFueraDeRango() {
    Map<String, Object> datos = Map.of("subestructura", Map.of("estribo_entrada_grietas", 9));

    assertThatThrownBy(() -> consulta.validarParcial(datos, SIECA))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            error ->
                assertThat(errores(error))
                    .singleElement()
                    .satisfies(
                        campo -> {
                          assertThat(campo.campo())
                              .isEqualTo("subestructura.estribo_entrada_grietas");
                          assertThat(campo.mensaje())
                              .isEqualTo("El valor está fuera del rango permitido.");
                        }));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("datosQueElEsquemaRechaza")
  @Transactional
  void validarCompletoConservaTodasLasReglasDeNetworknt(
      String caso, Map<String, Object> esquema, Map<String, Object> datos, String campo) {
    validacion.compilar(esquema);
    UUID id =
        versiones
            .saveAndFlush(
                new FormularioVersion(
                    "AUDIT-" + UUID.randomUUID().toString().substring(0, 8), esquema))
            .getId();
    assertThatThrownBy(() -> validacion.validar(id, datos)).isInstanceOf(NegocioException.class);
    assertThatThrownBy(() -> consulta.validarCompleto(datos, id))
        .isInstanceOf(NegocioException.class)
        .satisfies(error -> assertThat(rutas(error)).contains(campo));
  }

  static Stream<Arguments> datosQueElEsquemaRechaza() {
    Map<String, Object> texto = Map.of("type", "string");
    return Stream.of(
        Arguments.of(
            "required dentro de allOf",
            Map.of(
                "type", "object",
                "properties", Map.of("campo", texto),
                "allOf", List.of(Map.of("required", List.of("campo")))),
            Map.of(),
            "campo"),
        Arguments.of(
            "objeto requerido sin hojas requeridas",
            Map.of(
                "type", "object",
                "required", List.of("seccion"),
                "properties",
                    Map.of(
                        "seccion", Map.of("type", "object", "properties", Map.of("nota", texto)))),
            Map.of(),
            "seccion"),
        Arguments.of(
            "required dentro de una referencia",
            Map.of(
                "type", "object",
                "definitions",
                    Map.of(
                        "seccion",
                        Map.of(
                            "type", "object",
                            "required", List.of("campo"),
                            "properties", Map.of("campo", texto))),
                "properties", Map.of("seccion", Map.of("$ref", "#/definitions/seccion"))),
            Map.of("seccion", Map.of()),
            "seccion.campo"),
        Arguments.of(
            "null conserva su posicion en un arreglo",
            Map.of(
                "type",
                "object",
                "properties",
                Map.of("tramos", Map.of("type", "array", "items", Map.of("type", "object")))),
            Map.of("tramos", Arrays.asList((Object) null)),
            "tramos[0]"));
  }

  private static List<String> rutas(Throwable error) {
    return errores(error).stream().map(ErrorCampo::campo).toList();
  }

  @SuppressWarnings("unchecked")
  private static List<ErrorCampo> errores(Throwable error) {
    return (List<ErrorCampo>) ((NegocioException) error).getBody().getProperties().get("errores");
  }
}
