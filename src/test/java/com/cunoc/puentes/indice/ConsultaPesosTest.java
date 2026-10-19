package com.cunoc.puentes.indice;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.Rol;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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

/** Recorre HTTP, seguridad, servicio, MapStruct y PostgreSQL reales; fixtures con rollback. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql("/indice/consulta-pesos.sql")
class ConsultaPesosTest extends PruebaIntegracion {

  private static final String BASE = "/api/v1";
  private static final String FORMULARIO = "019a0019-0000-7000-8000-000000000001";
  private static final String VACIO = "019a0019-0000-7000-8000-000000000003";
  private static final String JUEGO = "019a0019-0000-7000-8000-000000000011";
  private static final String NUEVO = "019a0019-0000-7000-8000-000000000012";
  private static final String INEXISTENTE = "019a0019-0000-7000-8000-000000000099";
  private static final String LISTADO = BASE + "/formularios/" + FORMULARIO + "/juegos-pesos";
  private static final String DETALLE = BASE + "/juegos-pesos/" + JUEGO;

  @Autowired MockMvc mvc;
  @Autowired ConsultaPesosService servicio;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void limpiarContexto() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void administradorPaginaJuegosDelFormularioSinMezclarOtros() throws Exception {
    mvc.perform(get(LISTADO).param("size", "1").with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(NUEVO))
        .andExpect(jsonPath("$.content[0].formulario_version_id").value(FORMULARIO))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.total_elements").value(2))
        .andExpect(jsonPath("$.total_pages").value(2));
    mvc.perform(get(LISTADO).param("size", "1").param("page", "1").with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(JUEGO));
  }

  @Test
  void detalleConservaActaFechaYVersionSinSerializarEntidad() throws Exception {
    mvc.perform(get(DETALLE).with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(JUEGO))
        .andExpect(jsonPath("$.acta").value("actas/prueba.pdf"))
        .andExpect(jsonPath("$.vigente_desde").value("2026-10-19T12:00:00Z"))
        .andExpect(jsonPath("$.formulario_version_id").value(FORMULARIO))
        .andExpect(jsonPath("$.formularioVersionId").doesNotExist())
        .andExpect(jsonPath("$.creado_en").doesNotExist());
    mvc.perform(get(BASE + "/juegos-pesos/" + NUEVO).with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acta").isEmpty());
  }

  @Test
  void paginaElementosOrdenadosYPreservaValoresDeCadaJuego() throws Exception {
    mvc.perform(get(DETALLE + "/pesos").param("size", "1").with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].elemento_ref").value("elemento-a"))
        .andExpect(jsonPath("$.content[0].peso").value(1.25))
        .andExpect(jsonPath("$.content[0].versionPesos").doesNotExist())
        .andExpect(jsonPath("$.total_elements").value(2));
    mvc.perform(
            get(DETALLE + "/pesos")
                .param("size", "1")
                .param("page", "1")
                .with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].elemento_ref").value("elemento-b"))
        .andExpect(jsonPath("$.content[0].peso").value(2.5));
    mvc.perform(get(BASE + "/juegos-pesos/" + NUEVO + "/pesos").with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].peso").value(3.5));
  }

  @Test
  void formularioSinJuegosYPaginaFueraDelListadoDevuelvenPaginaVacia() throws Exception {
    mvc.perform(get(BASE + "/formularios/" + VACIO + "/juegos-pesos").with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.total_elements").value(0));
    mvc.perform(get(DETALLE + "/pesos").param("page", "10").with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.total_elements").value(2));
  }

  @Test
  void recursosInexistentesResponden404EnProblemDetails() throws Exception {
    for (String ruta : rutas(INEXISTENTE, INEXISTENTE)) {
      mvc.perform(get(ruta).with(como(Rol.ADMINISTRADOR)))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.detail").isNotEmpty())
          .andExpect(jsonPath("$.instance").value(ruta));
    }
  }

  @ParameterizedTest
  @CsvSource({"page,-1", "size,0", "size,101", "page,abc", "size,abc"})
  void rechazaPaginacionInvalidaEnAmbasColecciones(String campo, String valor) throws Exception {
    for (String ruta : List.of(LISTADO, DETALLE + "/pesos")) {
      mvc.perform(get(ruta).param(campo, valor).with(como(Rol.ADMINISTRADOR)))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.status").value(400));
    }
  }

  @Test
  void aceptaElLimiteDeCienEnAmbasColecciones() throws Exception {
    for (String ruta : List.of(LISTADO, DETALLE + "/pesos")) {
      mvc.perform(get(ruta).param("size", "100").with(como(Rol.ADMINISTRADOR)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.size").value(100));
    }
  }

  @Test
  void uuidMalFormadoResponde400EnLasTresRutas() throws Exception {
    for (String ruta : rutas("no-uuid", "no-uuid")) {
      mvc.perform(get(ruta).with(como(Rol.ADMINISTRADOR))).andExpect(status().isBadRequest());
    }
  }

  @ParameterizedTest
  @EnumSource(value = Rol.class, names = "ADMINISTRADOR", mode = EnumSource.Mode.EXCLUDE)
  void losOtrosRolesReciben403EnLasTresRutas(Rol rol) throws Exception {
    for (String ruta : rutas(FORMULARIO, JUEGO)) {
      mvc.perform(get(ruta).with(como(rol)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.status").value(403));
    }
  }

  @Test
  void visitanteRecibe401EnLasTresRutas() throws Exception {
    for (String ruta : rutas(FORMULARIO, JUEGO)) {
      mvc.perform(get(ruta))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.status").value(401));
    }
  }

  @ParameterizedTest
  @EnumSource(value = Rol.class, names = "ADMINISTRADOR", mode = EnumSource.Mode.EXCLUDE)
  void permisoTambienSeAplicaAlServicioSinControlador(Rol rol) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "usuario-prueba", "N/A", List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    assertThatThrownBy(() -> servicio.listar(UUID.fromString(FORMULARIO), 0, 20))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> servicio.obtener(UUID.fromString(JUEGO)))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> servicio.listarPesos(UUID.fromString(JUEGO), 0, 20))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void servicioSinSesionTambienDeniegaLasTresOperaciones() {
    SecurityContextHolder.clearContext();
    assertThatThrownBy(() -> servicio.listar(UUID.fromString(FORMULARIO), 0, 20))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    assertThatThrownBy(() -> servicio.obtener(UUID.fromString(JUEGO)))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    assertThatThrownBy(() -> servicio.listarPesos(UUID.fromString(JUEGO), 0, 20))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  void consultarNoModificaDatosNiGeneraAuditoriaDeEscritura() throws Exception {
    Long antes = jdbc.queryForObject("SELECT count(*) FROM auditoria", Long.class);
    for (String ruta : rutas(FORMULARIO, JUEGO)) {
      mvc.perform(get(ruta).with(como(Rol.ADMINISTRADOR))).andExpect(status().isOk());
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria", Long.class)).isEqualTo(antes);
    assertThat(
            jdbc.queryForObject(
                "SELECT peso FROM peso_elemento WHERE id = ?",
                Double.class,
                UUID.fromString("019a0019-0000-7000-8000-000000000022")))
        .isEqualTo(1.25);
  }

  private static List<String> rutas(String formulario, String juego) {
    return List.of(
        BASE + "/formularios/" + formulario + "/juegos-pesos",
        BASE + "/juegos-pesos/" + juego,
        BASE + "/juegos-pesos/" + juego + "/pesos");
  }
}
