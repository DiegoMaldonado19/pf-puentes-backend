package com.cunoc.puentes.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.common.NegocioException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExtractorElementosDeDanoTest {
  private static final UUID VERSION = UUID.fromString("019a0024-0000-7000-8000-000000000001");
  private final ExtractorElementosDeDano extractor = new ExtractorElementosDeDano();

  @Test
  void usaReferenciaCanonicaYNoInventaRutasNiCondicionesCriticas() {
    var campo = dano("subestructura.estribo_entrada.socavacion");
    campo.put("x-anulacion-critica", "socavacion_cimentacion");
    var esquema =
        objeto(Map.of("subestructura", objeto(Map.of("estribo_entrada_socavacion", campo))));
    var resultado = extractor.extraer(VERSION, esquema);
    assertThat(resultado.formularioVersionId()).isEqualTo(VERSION);
    assertThat(resultado.referencias()).containsExactly("subestructura.estribo_entrada.socavacion");
    assertThatThrownBy(() -> resultado.referencias().add("otra"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void recorreDefinicionDeArreglosSinExpandirInstanciasNiDecidirAgregacion() {
    var esquema =
        objeto(
            Map.of(
                "tramos",
                Map.of(
                    "type",
                    "array",
                    "items",
                    objeto(Map.of("deterioro", dano("tramos.deterioro"))))));
    assertThat(extractor.extraer(VERSION, esquema).referencias())
        .containsExactly("tramos.deterioro");
  }

  @Test
  void excluyeInventarioYCamposSinMarcaDeDanoYAdmiteEsquemaSinDanos() {
    var esquema =
        objeto(
            Map.of(
                "nombre",
                Map.of(
                    "type",
                    "string",
                    "x-permanente",
                    true,
                    "x-elemento-ref",
                    "identificacion.nombre"),
                "otro",
                Map.of("type", "integer", "x-dano", false)));
    assertThat(extractor.extraer(VERSION, esquema).referencias()).isEmpty();
    assertThat(extractor.extraer(VERSION, Map.of("type", "object")).referencias()).isEmpty();
  }

  @Test
  void noEliminaReferenciasDuplicadasSilenciosamente() {
    falla(objeto(Map.of("primero", dano("duplicado"), "segundo", dano("duplicado"))));
  }

  @ParameterizedTest
  @MethodSource("danosInvalidos")
  void rechazaMetadatosIncompatiblesSinDevolverUnConjuntoParcial(Map<String, Object> campo) {
    falla(objeto(Map.of("correcto", dano("correcto"), "invalido", campo)));
  }

  static Stream<Map<String, Object>> danosInvalidos() {
    return Stream.of(
        alterado("type", "number"),
        alterado("minimum", -1),
        alterado("minimum", "0"),
        alterado("minimum", Double.NaN),
        alterado("maximum", 5),
        alterado("maximum", "4"),
        sin("minimum"),
        sin("maximum"),
        sin("x-elemento-ref"),
        alterado("x-elemento-ref", " "),
        alterado("x-elemento-ref", 5),
        alterado("x-elemento-ref", "a".repeat(201)),
        alterado("x-dano", "true"),
        alterado("x-permanente", true),
        alterado("properties", Map.of()),
        alterado("items", Map.of("type", "string")));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"$ref", "allOf", "anyOf", "oneOf", "if", "then", "else", "patternProperties"})
  void rechazaEstructurasQueNecesitanOtroContratoSinOmitirDanos(String palabra) {
    var esquema = objeto(Map.of("danos", dano("campo")));
    esquema.put(palabra, List.of());
    falla(esquema);
  }

  @Test
  void rechazaRaizPropiedadesEItemsQueNoPuedeRecorrer() {
    falla(null);
    falla(Map.of("type", "array"));
    falla(Map.of("type", "object", "properties", "incorrecto"));
    falla(objeto(Map.of("campo", false)));
    falla(objeto(Map.of("campo", Map.of("type", "array"))));
    falla(objeto(Map.of("campo", Map.of("type", "array", "items", List.of()))));
    falla(Map.of("type", "object", "additionalProperties", dano("dinamico")));
    falla(
        objeto(
            Map.of(
                "campo", Map.of("type", "string", "properties", Map.of("dano", dano("irreal"))))));
    falla(objeto(Map.of("campo", Map.of("type", "object", "items", Map.of("type", "string")))));
  }

  @Test
  void versionEsObligatoriaYLosLimitesNumericosNoDependenDelTipoJava() {
    assertThatThrownBy(() -> extractor.extraer(null, objeto(Map.of())))
        .isInstanceOf(IllegalArgumentException.class);
    var campo = dano("a".repeat(200));
    campo.put("minimum", 0.0);
    campo.put("maximum", 4L);
    assertThat(extractor.extraer(VERSION, objeto(Map.of("campo", campo))).referencias()).hasSize(1);
  }

  static Map<String, Object> dano(String referencia) {
    return new HashMap<>(
        Map.of(
            "type",
            "integer",
            "minimum",
            0,
            "maximum",
            4,
            "x-dano",
            true,
            "x-elemento-ref",
            referencia));
  }

  static Map<String, Object> objeto(Map<String, ?> propiedades) {
    return new HashMap<>(Map.of("type", "object", "properties", propiedades));
  }

  private static Map<String, Object> alterado(String clave, Object valor) {
    var campo = dano("invalido");
    campo.put(clave, valor);
    return campo;
  }

  private static Map<String, Object> sin(String clave) {
    var campo = dano("invalido");
    campo.remove(clave);
    return campo;
  }

  private void falla(Map<String, Object> esquema) {
    assertThatThrownBy(() -> extractor.extraer(VERSION, esquema))
        .isInstanceOfSatisfying(
            NegocioException.class,
            e -> {
              assertThat(e.getStatusCode().value()).isEqualTo(422);
              assertThat(e.getBody().getType().getPath()).endsWith("metadatos-de-dano-invalidos");
            });
  }
}
