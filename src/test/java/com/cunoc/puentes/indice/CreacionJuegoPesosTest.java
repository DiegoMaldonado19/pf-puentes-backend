package com.cunoc.puentes.indice;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.usuario.Rol;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql({"/indice/servicio-pesos.sql", "/indice/creacion-juegos.sql"})
class CreacionJuegoPesosTest extends PruebaIntegracion {
  private static final UUID FORM = UUID.fromString("019a0018-0000-7000-8000-000000000001");
  private static final UUID ADMIN = UUID.fromString("019a0018-0000-7000-8000-000000000003");
  private static final UUID SIECA = UUID.fromString("0192b000-0000-7000-8000-000000000001");
  private static final String RUTA = "/api/v1/formularios/{id}/juegos-pesos";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entidades;
  @Autowired MetadatosDanoFormulario metadatos;
  @Autowired CreacionJuegoPesosService servicio;

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void creaCompletoConAuditoriaSinModificarHistoricosNiResultadosPublicados() throws Exception {
    var inspecciones = jdbc.queryForList("SELECT * FROM inspeccion");
    var anteriores = jdbc.queryForList("SELECT * FROM version_pesos");
    var pesos = jdbc.queryForList("SELECT * FROM peso_elemento");
    var formulario = jdbc.queryForMap("SELECT * FROM formulario_version WHERE id=?", FORM);
    UUID id = guardar(FORM, cuerpo("1.2500"));
    entidades.flush();
    assertThat(id.version()).isEqualTo(7);
    assertThat(jdbc.queryForList("SELECT * FROM inspeccion")).isEqualTo(inspecciones);
    assertThat(jdbc.queryForMap("SELECT * FROM formulario_version WHERE id=?", FORM))
        .isEqualTo(formulario);
    assertThat(jdbc.queryForList("SELECT * FROM version_pesos WHERE id<>?", id))
        .isEqualTo(anteriores);
    assertThat(jdbc.queryForList("SELECT * FROM peso_elemento WHERE version_pesos_id<>?", id))
        .isEqualTo(pesos);
    var auditorias = jdbc.queryForList("SELECT * FROM auditoria WHERE usuario_id=?", ADMIN);
    assertThat(auditorias).hasSize(3);
    Object correlacion = auditorias.getFirst().get("correlacion_id");
    assertThat(correlacion).isNotNull();
    assertThat(auditorias)
        .allSatisfy(
            fila ->
                assertThat(fila)
                    .containsEntry("accion", "CREAR")
                    .containsEntry("correlacion_id", correlacion));
  }

  @Test
  void permiteActivaRealConSusDiecinueveCamposSinPesosInventados() throws Exception {
    var definiciones =
        metadatos.obtener(SIECA).referencias().stream()
            .map(ref -> Map.of("elemento_ref", ref, "peso", 2))
            .toList();
    UUID id =
        guardar(
            SIECA,
            new ObjectMapper()
                .writeValueAsString(
                    Map.of("acta", "Referencia de prueba, no calibración", "pesos", definiciones)));
    entidades.flush();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM peso_elemento WHERE version_pesos_id=?", Long.class, id))
        .isEqualTo(19L);
  }

  @Test
  void admiteBorradorYJuegosProvisionalesNuevosSinSustituirElAnterior() throws Exception {
    jdbc.update(
        "UPDATE formulario_version SET estado='BORRADOR', publicada_en=null WHERE id=?", FORM);
    UUID uno = guardar(FORM, cuerpo("1"));
    UUID dos = guardar(FORM, cuerpo("2"));
    entidades.flush();
    assertThat(uno).isNotEqualTo(dos);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM version_pesos WHERE formulario_version_id=? AND acta IS NULL",
                Long.class,
                FORM))
        .isEqualTo(2L);
  }

  @ParameterizedTest
  @MethodSource("invalidos")
  void rechazaCuerposInvalidosSinEscrituras(String contenido) throws Exception {
    Long juegos = jdbc.queryForObject("SELECT count(*) FROM version_pesos", Long.class);
    mvc.perform(
            post(RUTA, FORM)
                .with(como(Rol.ADMINISTRADOR, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(contenido))
        .andExpect(status().isBadRequest());
    entidades.flush();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM version_pesos", Long.class))
        .isEqualTo(juegos);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE usuario_id=?", Long.class, ADMIN))
        .isZero();
  }

  static Stream<String> invalidos() {
    return Stream.of(
        "{}",
        "{\"pesos\":[]}",
        "{\"pesos\":[null]}",
        cuerpo("null"),
        cuerpo("0"),
        cuerpo("-1"),
        cuerpo("1000"),
        cuerpo("1.2345"),
        cuerpo("1").replace("elemento.b", "elemento.a"),
        cuerpo("1").replace("elemento.a", " "),
        cuerpo("1").replace("elemento.a", "a".repeat(201)),
        cuerpo("1").replace("{\"pesos\"", "{\"acta\":\" \",\"pesos\""),
        cuerpo("1").replace("{\"pesos\"", "{\"acta\":\"" + "a".repeat(301) + "\",\"pesos\""));
  }

  @Test
  void rechazaReferenciasFaltantesAjenasYEsquemasCambiados() throws Exception {
    for (String datos :
        List.of(
            "{\"pesos\":[{\"elemento_ref\":\"elemento.a\",\"peso\":1}]}",
            cuerpo("1").replace("elemento.b", "ajeno"))) {
      mvc.perform(
              post(RUTA, FORM)
                  .with(como(Rol.ADMINISTRADOR, ADMIN))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(datos))
          .andExpect(status().isUnprocessableContent());
    }
    jdbc.update("UPDATE formulario_version SET esquema='[]'::jsonb WHERE id=?", FORM);
    mvc.perform(
            post(RUTA, FORM)
                .with(como(Rol.ADMINISTRADOR, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("1")))
        .andExpect(status().isUnprocessableContent());
  }

  @ParameterizedTest
  @EnumSource(value = Rol.class, names = "ADMINISTRADOR", mode = EnumSource.Mode.EXCLUDE)
  void soloAdminEnHttpYServicio(Rol rol) throws Exception {
    mvc.perform(
            post(RUTA, FORM)
                .with(como(rol))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("1")))
        .andExpect(status().isForbidden());
    sesion(rol);
    assertThatThrownBy(() -> servicio.crear(FORM, null)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void validaSesionUuidAusenciaYParametrosInternos() throws Exception {
    mvc.perform(post(RUTA, FORM).contentType(MediaType.APPLICATION_JSON).content(cuerpo("1")))
        .andExpect(status().isUnauthorized());
    assertThatThrownBy(() -> servicio.crear(FORM, null))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    mvc.perform(
            post(RUTA, "incorrecto")
                .with(como(Rol.ADMINISTRADOR, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("1")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(RUTA, UUID.fromString("019a0040-0000-7000-8000-000000000099"))
                .with(como(Rol.ADMINISTRADOR, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("1")))
        .andExpect(status().isNotFound());
    sesion(Rol.ADMINISTRADOR);
    assertThatThrownBy(() -> servicio.crear(null, null)).isInstanceOf(NegocioException.class);
    assertThatThrownBy(() -> servicio.crear(FORM, null)).isInstanceOf(NegocioException.class);
  }

  private UUID guardar(UUID version, String contenido) throws Exception {
    var respuesta =
        mvc.perform(
                post(RUTA, version)
                    .with(como(Rol.ADMINISTRADOR, ADMIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(contenido))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.formulario_version_id").value(version.toString()))
            .andReturn()
            .getResponse();
    UUID id =
        UUID.fromString(
            new ObjectMapper().readTree(respuesta.getContentAsString()).get("id").asText());
    assertThat(respuesta.getHeader("Location")).isEqualTo("/api/v1/juegos-pesos/" + id);
    return id;
  }

  private static String cuerpo(String numero) {
    return "{\"pesos\":[{\"elemento_ref\":\"elemento.a\",\"peso\":"
        + numero
        + "},{\"elemento_ref\":\"elemento.b\",\"peso\":2}]}";
  }

  private void sesion(Rol rol) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                ADMIN.toString(),
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()))));
  }
}
