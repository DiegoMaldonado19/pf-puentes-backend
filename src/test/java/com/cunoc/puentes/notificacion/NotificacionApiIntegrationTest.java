package com.cunoc.puentes.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.SAME_THREAD)
@Import(NotificacionApiIntegrationTest.SeguridadPorMetodo.class)
class NotificacionApiIntegrationTest extends PruebaIntegracion {

  private static final String API = "/api/v1/notificaciones";
  private static final UUID USUARIO_A = id("0000000000a1");
  private static final UUID USUARIO_B = id("0000000000b1");
  private static final UUID A_NUEVA = id("0000000001a3");
  private static final UUID A_MEDIA = id("0000000001a2");
  private static final UUID A_LEIDA = id("0000000001a1");
  private static final UUID B_NUEVA = id("0000000001b2");
  private static final UUID B_LEIDA = id("0000000001b1");
  private static final UUID INEXISTENTE = id("000000000fff");
  private static final Instant LECTURA_EXISTENTE = Instant.parse("2026-10-01T12:34:56.123456Z");

  @TestConfiguration
  @EnableMethodSecurity
  static class SeguridadPorMetodo {}

  @Autowired MockMvc mvc;

  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void prepararDatos() {
    crearUsuario(USUARIO_A, "ESTUDIANTE");
    crearUsuario(USUARIO_B, "ADMINISTRADOR");
    crearAviso(A_LEIDA, USUARIO_A, "2026-10-01T10:00:00Z", LECTURA_EXISTENTE);
    crearAviso(A_MEDIA, USUARIO_A, "2026-10-01T11:00:00Z", null);
    crearAviso(A_NUEVA, USUARIO_A, "2026-10-01T11:00:00Z", null);
    crearAviso(B_LEIDA, USUARIO_B, "2026-10-01T10:00:00Z", LECTURA_EXISTENTE);
    crearAviso(B_NUEVA, USUARIO_B, "2026-10-01T11:00:00Z", null);
  }

  @AfterEach
  void limpiarDatos() {
    jdbc.update("DELETE FROM notificacion WHERE usuario_id IN (?, ?)", USUARIO_A, USUARIO_B);
    jdbc.update("DELETE FROM usuario WHERE id IN (?, ?)", USUARIO_A, USUARIO_B);
  }

  private void crearUsuario(UUID id, String rol) {
    jdbc.update(
        "INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol, estado, correo_verificado_en) VALUES (?, ?, ?, ?, ?, 'ACTIVO', now())",
        id,
        "notificacion-api-" + id + "@example.invalid",
        "Prueba notificaciones",
        "hash-no-utilizado-en-pruebas",
        rol);
  }

  private void crearAviso(UUID id, UUID usuario, String creadoEn, Instant leidaEn) {
    jdbc.update(
        "INSERT INTO notificacion (id, usuario_id, tipo, mensaje, enlace, creado_en, leida_en) VALUES (?, ?, ?, ?, ?, ?, ?)",
        id,
        usuario,
        "QA_P9_API",
        "QA P9 " + id,
        id.equals(A_LEIDA) ? null : "/qa/p9/" + id,
        OffsetDateTime.parse(creadoEn),
        leidaEn == null ? null : leidaEn.atOffset(java.time.ZoneOffset.UTC));
  }

  private record Actor(
      UUID id, String rol, UUID otro, UUID nueva, List<UUID> notificaciones, int noLeidas) {}

  private static Stream<Actor> actores() {
    return Stream.of(
        new Actor(
            USUARIO_A, "ESTUDIANTE", USUARIO_B, A_NUEVA, List.of(A_NUEVA, A_MEDIA, A_LEIDA), 2),
        new Actor(USUARIO_B, "ADMINISTRADOR", USUARIO_A, B_NUEVA, List.of(B_NUEVA, B_LEIDA), 1));
  }

  @ParameterizedTest
  @MethodSource("actores")
  void listadoYConteoSonDelPrincipalAunqueLaQueryIndiqueOtroUsuario(Actor actor) throws Exception {
    MvcResult listado =
        mvc.perform(
                get(API)
                    .param("usuario_id", actor.otro().toString())
                    .with(user(actor.id().toString()).roles(actor.rol())))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.pagina").value(0))
            .andExpect(jsonPath("$.tamano").value(20))
            .andExpect(jsonPath("$.total_elementos").value(actor.notificaciones().size()))
            .andExpect(jsonPath("$.total_paginas").value(1))
            .andReturn();

    assertThat(cuerpo(listado))
        .containsOnlyKeys("contenido", "pagina", "tamano", "total_elementos", "total_paginas");
    List<String> ids = JsonPath.read(json(listado), "$.contenido[*].id");
    assertThat(ids)
        .containsExactlyElementsOf(actor.notificaciones().stream().map(UUID::toString).toList());
    for (int indice = 0; indice < actor.notificaciones().size(); indice++) {
      comprobarDto(listado, "$.contenido[" + indice + "]", actor.notificaciones().get(indice));
    }
    comprobarConteo(actor, actor.noLeidas());
    assertThat(noLeidasEnBd(actor.id())).isEqualTo(actor.noLeidas());
  }

  @Test
  void paginaSoloLosRegistrosPropiosConOrdenEstableInclusoCuandoEmpatanLasFechas()
      throws Exception {
    comprobarPagina(0, 2, List.of(A_NUEVA, A_MEDIA), 2);
    comprobarPagina(1, 2, List.of(A_LEIDA), 2);
    comprobarPagina(2, 2, List.of(), 2);
    comprobarPagina(0, 100, List.of(A_NUEVA, A_MEDIA, A_LEIDA), 1);
  }

  @ParameterizedTest
  @CsvSource({
    "-1, 20",
    "0, 0",
    "0, -1",
    "0, 101",
    "texto, 20",
    "0, texto",
    "2147483647, 100",
    "2147483648, 20",
    "0, 2147483648",
    "9223372036854775808, 20",
    "0, 9223372036854775808"
  })
  void rechazaPaginacionInvalidaYOverflowSinCambiarLaBd(String pagina, String tamano)
      throws Exception {
    mvc.perform(
            get(API)
                .param("page", pagina)
                .param("size", tamano)
                .param("usuario_id", USUARIO_B.toString())
                .with(user(USUARIO_A.toString()).roles("ESTUDIANTE")))
        .andExpect(status().isBadRequest());

    assertThat(noLeidasEnBd(USUARIO_A)).isEqualTo(2);
    assertThat(noLeidasEnBd(USUARIO_B)).isEqualTo(1);
    assertThat(totalEnBd(USUARIO_A)).isEqualTo(3);
    assertThat(totalEnBd(USUARIO_B)).isEqualTo(2);
  }

  @ParameterizedTest
  @MethodSource("actores")
  void marcarYRepetirLecturaConservaLaPrimeraFechaPersistidaYNoTocaAlOtroUsuario(Actor actor)
      throws Exception {
    UUID ajena = actor.otro().equals(USUARIO_A) ? A_NUEVA : B_NUEVA;
    assertThat(fechaEnBd(actor.nueva(), "leida_en")).isNull();
    assertThat(fechaEnBd(ajena, "leida_en")).isNull();
    comprobarConteo(actor, actor.noLeidas());

    MvcResult primera = marcar(actor, actor.nueva());
    Instant persistida = fechaEnBd(actor.nueva(), "leida_en");
    assertThat(persistida).isNotNull();
    assertThat(persistida.getNano() % 1_000).isZero();
    comprobarDto(primera, "$", actor.nueva());
    assertThat(fechaJson(primera, "$.leida_en")).isEqualTo(persistida);
    comprobarConteo(actor, actor.noLeidas() - 1);

    MvcResult repetida = marcar(actor, actor.nueva());
    comprobarDto(repetida, "$", actor.nueva());
    assertThat(fechaEnBd(actor.nueva(), "leida_en")).isEqualTo(persistida);
    assertThat(fechaJson(repetida, "$.leida_en")).isEqualTo(persistida);
    assertThat(fechaEnBd(ajena, "leida_en")).isNull();
    assertThat(noLeidasEnBd(actor.id())).isEqualTo(actor.noLeidas() - 1);
    assertThat(noLeidasEnBd(actor.otro())).isEqualTo(actor.otro().equals(USUARIO_A) ? 2 : 1);
    assertThat(totalEnBd(actor.id())).isEqualTo(actor.notificaciones().size());
    assertThat(totalEnBd(actor.otro())).isEqualTo(actor.otro().equals(USUARIO_A) ? 3 : 2);
    comprobarConteo(actor, actor.noLeidas() - 1);
  }

  @Test
  void unaLecturaExistenteConMicrosegundosSeDevuelveExactaSinReescribirla() throws Exception {
    Actor actor = actores().findFirst().orElseThrow();
    assertThat(fechaEnBd(A_LEIDA, "leida_en")).isEqualTo(LECTURA_EXISTENTE);

    for (int intento = 0; intento < 2; intento++) {
      MvcResult respuesta = marcar(actor, A_LEIDA);
      comprobarDto(respuesta, "$", A_LEIDA);
      assertThat(fechaJson(respuesta, "$.leida_en")).isEqualTo(LECTURA_EXISTENTE);
      assertThat(fechaEnBd(A_LEIDA, "leida_en")).isEqualTo(LECTURA_EXISTENTE);
    }
    comprobarConteo(actor, 2);
    assertThat(noLeidasEnBd(USUARIO_A)).isEqualTo(2);
    assertThat(noLeidasEnBd(USUARIO_B)).isEqualTo(1);
  }

  @ParameterizedTest
  @MethodSource("actores")
  void ajenaEInexistenteResponden404UniformeInclusoParaAdministrador(Actor actor) throws Exception {
    UUID ajena = actor.otro().equals(USUARIO_A) ? A_NUEVA : B_NUEVA;
    MvcResult respuestaAjena = marcarNoEncontrada(actor, ajena);
    MvcResult respuestaInexistente = marcarNoEncontrada(actor, INEXISTENTE);
    Map<String, Object> problemaAjeno = new HashMap<>(cuerpo(respuestaAjena));
    Map<String, Object> problemaInexistente = new HashMap<>(cuerpo(respuestaInexistente));

    assertThat(problemaAjeno)
        .containsKeys("type", "title", "status", "detail", "instance")
        .containsEntry("status", 404)
        .containsEntry("instance", API + "/" + ajena + "/lectura")
        .doesNotContainKeys("usuario_id", "mensaje", "enlace");
    assertThat(problemaInexistente).containsEntry("instance", API + "/" + INEXISTENTE + "/lectura");
    problemaAjeno.remove("instance");
    problemaInexistente.remove("instance");
    assertThat(problemaAjeno).isEqualTo(problemaInexistente);
    assertThat(fechaEnBd(ajena, "leida_en")).isNull();
    assertThat(fechaEnBd(actor.nueva(), "leida_en")).isNull();
    assertThat(noLeidasEnBd(USUARIO_A)).isEqualTo(2);
    assertThat(noLeidasEnBd(USUARIO_B)).isEqualTo(1);
    assertThat(totalEnBd(USUARIO_A)).isEqualTo(3);
    assertThat(totalEnBd(USUARIO_B)).isEqualTo(2);
    comprobarConteo(actor, actor.noLeidas());
  }

  private void comprobarPagina(int pagina, int tamano, List<UUID> esperadas, int totalPaginas)
      throws Exception {
    MvcResult respuesta =
        mvc.perform(
                get(API)
                    .param("page", Integer.toString(pagina))
                    .param("size", Integer.toString(tamano))
                    .param("usuario_id", USUARIO_B.toString())
                    .with(user(USUARIO_A.toString()).roles("ESTUDIANTE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagina").value(pagina))
            .andExpect(jsonPath("$.tamano").value(tamano))
            .andExpect(jsonPath("$.total_elementos").value(3))
            .andExpect(jsonPath("$.total_paginas").value(totalPaginas))
            .andReturn();
    List<String> ids = JsonPath.read(json(respuesta), "$.contenido[*].id");
    assertThat(ids).containsExactlyElementsOf(esperadas.stream().map(UUID::toString).toList());
  }

  private void comprobarConteo(Actor actor, int esperado) throws Exception {
    MvcResult respuesta =
        mvc.perform(
                get(API + "/no-leidas/conteo")
                    .param("usuario_id", actor.otro().toString())
                    .with(user(actor.id().toString()).roles(actor.rol())))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.total").value(esperado))
            .andReturn();
    assertThat(cuerpo(respuesta)).containsOnlyKeys("total");
  }

  private MvcResult marcar(Actor actor, UUID notificacion) throws Exception {
    return mvc.perform(
            patch(API + "/{id}/lectura", notificacion)
                .param("usuario_id", actor.otro().toString())
                .with(user(actor.id().toString()).roles(actor.rol()))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andReturn();
  }

  private MvcResult marcarNoEncontrada(Actor actor, UUID notificacion) throws Exception {
    return mvc.perform(
            patch(API + "/{id}/lectura", notificacion)
                .param("usuario_id", actor.otro().toString())
                .with(user(actor.id().toString()).roles(actor.rol()))
                .with(csrf()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andReturn();
  }

  private void comprobarDto(MvcResult respuesta, String ruta, UUID esperado) throws Exception {
    Map<String, Object> dto = JsonPath.read(json(respuesta), ruta);
    assertThat(dto)
        .containsOnlyKeys("id", "tipo", "mensaje", "enlace", "creado_en", "leida_en")
        .containsEntry("id", esperado.toString())
        .containsEntry("tipo", "QA_P9_API")
        .containsEntry("mensaje", "QA P9 " + esperado)
        .containsEntry("enlace", esperado.equals(A_LEIDA) ? null : "/qa/p9/" + esperado);
    assertThat(fechaJson(respuesta, ruta + ".creado_en"))
        .isEqualTo(fechaEnBd(esperado, "creado_en"));
    Instant persistida = fechaEnBd(esperado, "leida_en");
    if (persistida == null) {
      assertThat(dto.get("leida_en")).isNull();
    } else {
      assertThat(fechaJson(respuesta, ruta + ".leida_en")).isEqualTo(persistida);
    }
  }

  private Instant fechaEnBd(UUID notificacion, String columna) {
    String consulta =
        switch (columna) {
          case "creado_en" -> "SELECT creado_en FROM notificacion WHERE id = ?";
          case "leida_en" -> "SELECT leida_en FROM notificacion WHERE id = ?";
          default -> throw new IllegalArgumentException("Columna de fecha no permitida");
        };
    return jdbc.queryForObject(
        consulta,
        (fila, numero) -> {
          OffsetDateTime fecha = fila.getObject(1, OffsetDateTime.class);
          return fecha == null ? null : fecha.toInstant();
        },
        notificacion);
  }

  private int noLeidasEnBd(UUID usuario) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM notificacion WHERE usuario_id = ? AND leida_en IS NULL",
        Integer.class,
        usuario);
  }

  private int totalEnBd(UUID usuario) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM notificacion WHERE usuario_id = ?", Integer.class, usuario);
  }

  private static Instant fechaJson(MvcResult respuesta, String ruta) throws Exception {
    String fecha = JsonPath.read(json(respuesta), ruta);
    return Instant.parse(fecha);
  }

  private static Map<String, Object> cuerpo(MvcResult respuesta) throws Exception {
    return JsonPath.read(json(respuesta), "$");
  }

  private static String json(MvcResult respuesta) throws Exception {
    return respuesta.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private static UUID id(String sufijo) {
    return UUID.fromString("019c1958-34a9-7000-8000-" + sufijo);
  }
}
