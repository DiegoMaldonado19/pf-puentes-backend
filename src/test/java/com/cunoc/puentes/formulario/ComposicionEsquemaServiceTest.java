package com.cunoc.puentes.formulario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.cunoc.puentes.common.ErrorCampo;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.formulario.dto.CampoExtraDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ComposicionEsquemaServiceTest {

  private static final Set<String> RESERVADAS =
      Set.of("x-dano", "x-permanente", "x-grupo", "x-anulacion-critica", "x-elemento-ref");

  private ComposicionEsquemaService servicio;
  private FormularioValidacionServiceImpl validador;

  @BeforeEach
  void setUp() {
    servicio = new ComposicionEsquemaService();
    validador =
        new FormularioValidacionServiceImpl(
            mock(FormularioVersionRepository.class), new ObjectMapper());
  }

  @Test
  @DisplayName("El esquema base expone sus ocho secciones y cierra identificacion y tramos")
  void esquemaBaseListaSeccionesYMarcaLasCerradas() {
    var base = servicio.esquemaBase();

    assertThat(base.secciones()).hasSize(8);
    assertThat(base.secciones())
        .filteredOn(seccion -> !seccion.admiteCamposExtra())
        .extracting(seccion -> seccion.clave())
        .containsExactlyInAnyOrder("identificacion", "tramos");
  }

  @Test
  @DisplayName("Sin campos adicionales la composición es exactamente el esquema base")
  void sinCamposExtraLaComposicionEsLaBase() {
    assertThat(servicio.componer(List.of())).isEqualTo(servicio.esquemaBase().esquema());
    assertThat(servicio.componer(null)).isEqualTo(servicio.esquemaBase().esquema());
  }

  @Test
  @DisplayName("Un campo obligatorio entra en required, se marca x-extra y continúa el orden")
  void componeCampoConMetadatosYOrdenCorrelativo() {
    var esquema =
        servicio.componer(
            List.of(
                new CampoExtraDTO(
                    "datos_generales",
                    "observacion_general",
                    "Observación general",
                    "texto_largo",
                    true,
                    null,
                    null,
                    null,
                    null,
                    "Describa el estado general"),
                new CampoExtraDTO(
                    "datos_generales",
                    "altura_libre",
                    "Altura libre",
                    "numero",
                    false,
                    null,
                    BigDecimal.ZERO,
                    new BigDecimal("100"),
                    "m",
                    null)));

    var seccion = seccion(esquema, "datos_generales");
    var campos = mapa(seccion.get("properties"));
    var primero = mapa(campos.get("observacion_general"));
    var segundo = mapa(campos.get("altura_libre"));

    assertThat(primero)
        .containsEntry("title", "Observación general")
        .containsEntry("description", "Describa el estado general")
        .containsEntry("type", "string")
        .containsEntry("maxLength", 4000)
        .containsEntry("x-control", "textarea")
        .containsEntry("x-extra", true);
    assertThat(segundo)
        .containsEntry("type", "number")
        .containsEntry("minimum", BigDecimal.ZERO)
        .containsEntry("maximum", new BigDecimal("100"))
        .containsEntry("x-unidad", "m")
        .containsEntry("x-extra", true);
    assertThat(segundo.get("x-orden")).isEqualTo(((Number) primero.get("x-orden")).intValue() + 1);
    assertThat(requeridos(seccion)).contains("observacion_general").doesNotContain("altura_libre");
  }

  @Test
  @DisplayName("Cada tipo se traduce al JSON Schema que el motor sabe validar")
  void tiposSeTraducenAJsonSchema() {
    var esquema =
        servicio.componer(
            List.of(
                campoDeTipo("t_texto", "texto"),
                campoDeTipo("t_entero", "entero"),
                campoDeTipo("t_si_no", "si_no"),
                campoDeTipo("t_fecha", "fecha"),
                new CampoExtraDTO(
                    "otros",
                    "t_opcion",
                    "Opción",
                    "opcion",
                    false,
                    List.of(" Bueno ", "Malo"),
                    null,
                    null,
                    null,
                    null)));

    var campos = mapa(seccion(esquema, "otros").get("properties"));
    assertThat(mapa(campos.get("t_texto")))
        .containsEntry("type", "string")
        .containsEntry("maxLength", 500);
    assertThat(mapa(campos.get("t_entero"))).containsEntry("type", "integer");
    assertThat(mapa(campos.get("t_si_no"))).containsEntry("type", "boolean");
    assertThat(mapa(campos.get("t_fecha")))
        .containsEntry("type", "string")
        .containsEntry("format", "date");
    assertThat(mapa(campos.get("t_opcion")).get("enum")).isEqualTo(List.of("Bueno", "Malo"));
  }

  @Test
  @DisplayName("Ningún campo adicional puede llevar extensiones que alteren IC, pesos o precarga")
  void camposComponidosNoTrasladanExtensionesReservadas() {
    var extras = new ArrayList<CampoExtraDTO>();
    for (String tipo : ComposicionEsquemaService.TIPOS) {
      extras.add(campoDeTipo("c_" + tipo.replace("_", ""), tipo));
    }
    var campos = mapa(seccion(servicio.componer(extras), "otros").get("properties"));
    var extrasCompuestos =
        campos.entrySet().stream()
            .filter(entrada -> Boolean.TRUE.equals(mapa(entrada.getValue()).get("x-extra")))
            .toList();

    assertThat(extrasCompuestos).hasSize(ComposicionEsquemaService.TIPOS.size());
    for (var entrada : extrasCompuestos) {
      assertThat(mapa(entrada.getValue()).keySet()).doesNotContainAnyElementsOf(RESERVADAS);
    }
  }

  @Test
  @DisplayName("Las secciones identificacion y tramos no admiten campos adicionales")
  void rechazaSeccionesCerradasDesconocidasYVacias() {
    List<ErrorCampo> errores =
        servicio.validar(
            List.of(
                campo("identificacion", "campo_uno"),
                campo("tramos", "campo_dos"),
                campo("no_existe", "campo_tres"),
                campo(null, "campo_cuatro")));

    assertThat(errores)
        .extracting(ErrorCampo::campo)
        .containsExactly(
            "campos_extra[0].seccion",
            "campos_extra[1].seccion",
            "campos_extra[2].seccion",
            "campos_extra[3].seccion");
    assertThat(errores.get(0).mensaje()).contains("no admite campos adicionales");
    assertThat(errores.get(3).mensaje()).contains("Indique la sección");
  }

  @Test
  @DisplayName("La clave sigue el patrón, no repite campos del base ni de otros extras")
  void rechazaClavesInvalidasDuplicadasYExistentes() {
    List<ErrorCampo> errores =
        servicio.validar(
            List.of(
                campo("otros", "Mayusculas"),
                campo("otros", "ab"),
                campo("otros", "x-dano"),
                campo("otros", "a".repeat(42)),
                campo("otros", "repetida"),
                campo("otros", "repetida"),
                campo("datos_generales", "longitud_total"),
                campo("otros", null)));

    assertThat(errores)
        .extracting(ErrorCampo::campo)
        .containsExactly(
            "campos_extra[0].clave",
            "campos_extra[1].clave",
            "campos_extra[2].clave",
            "campos_extra[3].clave",
            "campos_extra[5].clave",
            "campos_extra[6].clave",
            "campos_extra[7].clave");
  }

  @Test
  @DisplayName("El título es obligatorio y admite hasta 120 caracteres")
  void validaTitulo() {
    var sinTitulo = campo("otros", "clave_valida");
    var largo =
        new CampoExtraDTO(
            "otros", "clave_larga", "x".repeat(121), "texto", false, null, null, null, null, null);
    var vacio =
        new CampoExtraDTO(
            "otros", "clave_vacia", "   ", "texto", false, null, null, null, null, null);

    assertThat(servicio.validar(List.of(largo, vacio)))
        .extracting(ErrorCampo::campo)
        .containsExactly("campos_extra[0].titulo", "campos_extra[1].titulo");
    assertThat(servicio.validar(List.of(sinTitulo))).isEmpty();
  }

  @Test
  @DisplayName("Solo se aceptan los siete tipos del constructor")
  void rechazaTipoDesconocidoOAusente() {
    var desconocido =
        new CampoExtraDTO(
            "otros", "clave_tipo", "Título", "email", false, null, null, null, null, null);
    var ausente =
        new CampoExtraDTO(
            "otros", "clave_sin", "Título", null, false, null, null, null, null, null);

    assertThat(servicio.validar(List.of(desconocido, ausente)))
        .extracting(ErrorCampo::campo)
        .containsExactly("campos_extra[0].tipo", "campos_extra[1].tipo");
  }

  @Test
  @DisplayName("Un campo de opción necesita opciones válidas, únicas y en cantidad razonable")
  void validaOpciones() {
    var sinOpciones = opcion("opc_vacia", List.of());
    var repetidas = opcion("opc_repetida", List.of("Sí", "Sí"));
    var demasiadas =
        opcion(
            "opc_muchas", java.util.stream.IntStream.range(0, 51).mapToObj(i -> "o" + i).toList());
    var enBlanco = opcion("opc_blanco", java.util.Arrays.asList("A", " "));
    var noOpcion =
        new CampoExtraDTO(
            "otros",
            "opc_en_texto",
            "Título",
            "texto",
            false,
            List.of("A"),
            null,
            null,
            null,
            null);

    assertThat(servicio.validar(List.of(sinOpciones, repetidas, demasiadas, enBlanco, noOpcion)))
        .extracting(ErrorCampo::campo)
        .containsExactly(
            "campos_extra[0].opciones",
            "campos_extra[1].opciones",
            "campos_extra[2].opciones",
            "campos_extra[3].opciones",
            "campos_extra[4].opciones");
  }

  @Test
  @DisplayName("Los límites solo aplican a numéricos, con mínimo no mayor que máximo")
  void validaLimites() {
    var limitesEnTexto =
        new CampoExtraDTO(
            "otros", "lim_texto", "Título", "texto", false, null, BigDecimal.ONE, null, null, null);
    var invertidos =
        new CampoExtraDTO(
            "otros",
            "lim_invertidos",
            "Título",
            "numero",
            false,
            null,
            BigDecimal.TEN,
            BigDecimal.ONE,
            null,
            null);
    var enteroDecimal =
        new CampoExtraDTO(
            "otros",
            "lim_entero",
            "Título",
            "entero",
            false,
            null,
            new BigDecimal("1.5"),
            null,
            null,
            null);
    var numeroDecimal =
        new CampoExtraDTO(
            "otros",
            "lim_decimal",
            "Título",
            "numero",
            false,
            null,
            new BigDecimal("1.5"),
            new BigDecimal("2.0"),
            null,
            null);

    assertThat(servicio.validar(List.of(limitesEnTexto, invertidos, enteroDecimal, numeroDecimal)))
        .extracting(ErrorCampo::campo)
        .containsExactly(
            "campos_extra[0].minimo", "campos_extra[1].maximo", "campos_extra[2].minimo");
  }

  @Test
  @DisplayName("La unidad solo aplica a numéricos y admite hasta 20 caracteres")
  void validaUnidad() {
    var enTexto =
        new CampoExtraDTO(
            "otros", "uni_texto", "Título", "texto", false, null, null, null, "m", null);
    var larga =
        new CampoExtraDTO(
            "otros",
            "uni_larga",
            "Título",
            "numero",
            false,
            null,
            null,
            null,
            "u".repeat(21),
            null);

    assertThat(servicio.validar(List.of(enTexto, larga)))
        .extracting(ErrorCampo::campo)
        .containsExactly("campos_extra[0].unidad", "campos_extra[1].unidad");
  }

  @Test
  @DisplayName("La ayuda admite hasta 300 caracteres")
  void validaAyuda() {
    var larga =
        new CampoExtraDTO(
            "otros",
            "ayuda_larga",
            "Título",
            "texto",
            false,
            null,
            null,
            null,
            null,
            "a".repeat(301));

    assertThat(servicio.validar(List.of(larga)))
        .extracting(ErrorCampo::campo)
        .containsExactly("campos_extra[0].ayuda");
  }

  @Test
  @DisplayName("Un formulario admite como máximo cuarenta campos adicionales")
  void limitaCantidadDeCampos() {
    var extras = new ArrayList<CampoExtraDTO>();
    for (int i = 0; i <= 40; i++) {
      extras.add(campo("otros", "campo_" + String.format("%03d", i)));
    }

    assertThat(servicio.validar(extras))
        .extracting(ErrorCampo::campo)
        .containsExactly("campos_extra");
  }

  @Test
  @DisplayName("Un campo nulo se reporta como vacío y no rompe la validación")
  void campoNuloSeReportaComoVacio() {
    List<CampoExtraDTO> extras = new ArrayList<>();
    extras.add(null);

    assertThat(servicio.validar(extras))
        .extracting(ErrorCampo::mensaje)
        .containsExactly("El campo adicional está vacío.");
  }

  @Test
  @DisplayName("componer rechaza con 422 y errores por campo; no devuelve un esquema a medias")
  void componerLanzaUnprocesableConErrores() {
    assertThatThrownBy(() -> servicio.componer(List.of(campo("tramos", "clave_bien"))))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            error -> {
              var negocio = (NegocioException) error;
              assertThat(negocio.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(negocio.getBody().getProperties()).containsKey("errores");
            });
  }

  @Test
  @DisplayName("La composición con todos los tipos es un JSON Schema válido para el motor")
  void composicionCompilaComoJsonSchema() {
    var extras = new ArrayList<CampoExtraDTO>();
    for (String tipo : ComposicionEsquemaService.TIPOS) {
      extras.add(
          new CampoExtraDTO(
              "otros",
              "c_" + tipo.replace("_", ""),
              "Título",
              tipo,
              true,
              "opcion".equals(tipo) ? List.of("A", "B") : null,
              "numero".equals(tipo) || "entero".equals(tipo) ? BigDecimal.ZERO : null,
              "numero".equals(tipo) || "entero".equals(tipo) ? BigDecimal.TEN : null,
              "numero".equals(tipo) ? "m" : null,
              "Ayuda"));
    }

    validador.compilar(servicio.componer(extras));
  }

  private static CampoExtraDTO campo(String seccion, String clave) {
    return new CampoExtraDTO(
        seccion, clave, "Título de " + clave, "texto", false, null, null, null, null, null);
  }

  private static CampoExtraDTO campoDeTipo(String clave, String tipo) {
    return new CampoExtraDTO(
        "otros",
        clave,
        "Título " + tipo,
        tipo,
        false,
        "opcion".equals(tipo) ? List.of("A") : null,
        null,
        null,
        null,
        null);
  }

  @SuppressWarnings("unchecked")
  private static List<Object> requeridos(Map<String, Object> seccion) {
    return (List<Object>) seccion.get("required");
  }

  private static CampoExtraDTO opcion(String clave, List<String> opciones) {
    return new CampoExtraDTO(
        "otros", clave, "Título", "opcion", false, opciones, null, null, null, null);
  }

  private static Map<String, Object> seccion(Map<String, Object> esquema, String clave) {
    return mapa(mapa(esquema.get("properties")).get(clave));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapa(Object valor) {
    return (Map<String, Object>) valor;
  }
}
