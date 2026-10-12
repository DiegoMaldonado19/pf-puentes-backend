package com.cunoc.puentes.common.seguridad;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.usuario.EstadoCuentaService;
import com.cunoc.puentes.usuario.Rol;
import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La cadena de filtros real contra un controlador de prueba: el filtro JWT y las reglas de ruta.
 */
@WebMvcTest(
    controllers = CadenaDeFiltrosTest.ControladorPrueba.class,
    properties = {
      "sgp.seguridad.rutas-publicas=GET /api/v1/prueba/publica, /api/v1/prueba/abierta/**"
    })
@Import({SeguridadDePrueba.class, CadenaDeFiltrosTest.ControladorPrueba.class})
class CadenaDeFiltrosTest {

  private static final UUID USUARIO = UUID.fromString("0192f5a0-0000-7000-8000-000000000002");

  @Autowired MockMvc mvc;
  @Autowired TokenService tokens;

  @MockitoBean EstadoCuentaService cuentas;

  @BeforeEach
  void cuentaActiva() {
    given(cuentas.rolSiEstaActiva(USUARIO)).willReturn(Optional.of(Rol.ESTUDIANTE));
  }

  @Test
  void rutaPrivadaSinTokenResponde401ConProblemDetailsYBearer() throws Exception {
    mvc.perform(get("/api/v1/prueba/privada"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("https://sgp.cunoc.usac.edu.gt/errores/no-autenticado"))
        .andExpect(jsonPath("$.title").isNotEmpty())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.detail").isNotEmpty())
        .andExpect(jsonPath("$.instance").value("/api/v1/prueba/privada"));
  }

  @Test
  void unTokenValidDejaEntrarYElServicioObtieneIdYRol() throws Exception {
    mvc.perform(get("/api/v1/prueba/privada").header(HttpHeaders.AUTHORIZATION, bearer(token())))
        .andExpect(status().isOk())
        .andExpect(content().string(USUARIO + " [ROLE_ESTUDIANTE]"));
  }

  @Test
  void elRolSaleDeLaBaseNoDelToken() throws Exception {
    given(cuentas.rolSiEstaActiva(USUARIO)).willReturn(Optional.of(Rol.ADMINISTRADOR));

    mvc.perform(get("/api/v1/prueba/admin").header(HttpHeaders.AUTHORIZATION, bearer(token())))
        .andExpect(status().isOk());
  }

  @Test
  void unTokenExpiradoResponde401() throws Exception {
    var pasado =
        new TokenService(
            SeguridadDePrueba.SECRETO, Clock.offset(Clock.systemUTC(), Duration.ofMinutes(-16)));

    rechazado(pasado.emitirAcceso(USUARIO, Rol.ESTUDIANTE));
  }

  @Test
  void unTokenConLaFirmaAlteradaResponde401() throws Exception {
    rechazado(token() + "x");
  }

  @Test
  void unTokenFirmadoConOtraClaveResponde401() throws Exception {
    rechazado(
        new TokenService("otra-clave-distinta-de-al-menos-32-bytes")
            .emitirAcceso(USUARIO, Rol.ESTUDIANTE));
  }

  @Test
  void unTokenMalFormadoResponde401() throws Exception {
    rechazado("esto-no-es-un-jwt");
  }

  @Test
  void unaCabeceraQueNoEsBearerResponde401() throws Exception {
    mvc.perform(get("/api/v1/prueba/privada").header(HttpHeaders.AUTHORIZATION, "Basic " + token()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unaCuentaQueDejoDeEstarActivaPierdeElAccesoConSuTokenVigente() throws Exception {
    String token = token();
    given(cuentas.rolSiEstaActiva(USUARIO)).willReturn(Optional.empty());

    rechazado(token);
  }

  @Test
  void elRechazoNoRevelaElMotivoTecnico() throws Exception {
    String detalleSinToken =
        mvc.perform(get("/api/v1/prueba/privada")).andReturn().getResponse().getContentAsString();

    mvc.perform(get("/api/v1/prueba/privada").header(HttpHeaders.AUTHORIZATION, bearer("a.b.c")))
        .andExpect(content().json(detalleSinToken));
  }

  @Test
  void unRolSinPermisoRecibe403EnProblemDetails() throws Exception {
    mvc.perform(get("/api/v1/prueba/admin").header(HttpHeaders.AUTHORIZATION, bearer(token())))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(
            jsonPath("$.type").value("https://sgp.cunoc.usac.edu.gt/errores/acceso-denegado"))
        .andExpect(jsonPath("$.title").isNotEmpty())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.detail").isNotEmpty());
  }

  @Test
  void laRutaDeclaradaPublicaSeConsultaSinToken() throws Exception {
    mvc.perform(get("/api/v1/prueba/publica")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/prueba/abierta/uno")).andExpect(status().isOk());
  }

  @Test
  void laDeclaracionPublicaRespetaElMetodo() throws Exception {
    mvc.perform(post("/api/v1/prueba/publica")).andExpect(status().isUnauthorized());
  }

  @Test
  void unaRutaPublicaIgualSeAtiendeConTokenInvalido() throws Exception {
    mvc.perform(get("/api/v1/prueba/publica").header(HttpHeaders.AUTHORIZATION, bearer("a.b.c")))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/prueba/nueva", "/api/v1/prueba/privada/otra", "/api/v1/nada"})
  void loNoDeclaradoEsPrivadoPorDefecto(String ruta) throws Exception {
    mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
  }

  private ResultActions rechazado(String token) throws Exception {
    return mvc.perform(
            get("/api/v1/prueba/privada").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
        .andExpect(jsonPath("$.status").value(401));
  }

  private String token() {
    return tokens.emitirAcceso(USUARIO, Rol.ESTUDIANTE);
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  @RestController
  static class ControladorPrueba {

    @GetMapping("/api/v1/prueba/privada")
    String privada(Principal usuario, Authentication autenticacion) {
      return usuario.getName() + " " + autenticacion.getAuthorities();
    }

    @GetMapping("/api/v1/prueba/admin")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    String admin() {
      return "ok";
    }

    @GetMapping({"/api/v1/prueba/publica", "/api/v1/prueba/abierta/uno"})
    String publica() {
      return "ok";
    }

    @PostMapping("/api/v1/prueba/publica")
    String publicaPost() {
      return "ok";
    }
  }
}
