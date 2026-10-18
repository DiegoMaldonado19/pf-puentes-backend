package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import com.cunoc.puentes.usuario.AutenticacionService.SesionIniciada;
import com.cunoc.puentes.usuario.dto.SesionDTO;
import com.cunoc.puentes.usuario.dto.UsuarioSesionDTO;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** El contrato HTTP del login: forma de la respuesta, cookie y errores. */
@WebMvcTest(AutenticacionController.class)
@Import({SeguridadDePrueba.class, CookieRenovacion.class})
class AutenticacionControllerTest {

  private static final String LOGIN = "/api/v1/auth/login";
  private static final String CREDENCIALES =
      """
      {"correo": "  CARLOS@Correo.com ", "contrasena": "Puentes2026ok"}
      """;

  @Autowired MockMvc mvc;

  @MockitoBean AutenticacionService autenticacion;

  private static MockHttpServletRequestBuilder login(String cuerpo) {
    return post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
  }

  private static SesionIniciada sesion() {
    UsuarioSesionDTO usuario =
        new UsuarioSesionDTO(UUID.randomUUID(), "Carlos Ruiz", Rol.CATEDRATICO);
    return new SesionIniciada(
        new SesionDTO("jwt.de.acceso", SesionDTO.BEARER, 900, usuario),
        "token-de-renovacion",
        Duration.ofDays(7));
  }

  @Test
  void respondeConElTokenDeAccesoYLaCookieDeRenovacion() throws Exception {
    when(autenticacion.iniciarSesion(eq("carlos@correo.com"), any())).thenReturn(sesion());

    mvc.perform(login(CREDENCIALES))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").value("jwt.de.acceso"))
        .andExpect(jsonPath("$.token_type").value("Bearer"))
        .andExpect(jsonPath("$.expires_in").value(900))
        .andExpect(jsonPath("$.usuario.nombre").value("Carlos Ruiz"))
        .andExpect(jsonPath("$.usuario.rol").value("CATEDRATICO"))
        .andExpect(jsonPath("$.usuario.id").exists())
        .andExpect(jsonPath("$.refresh_token").doesNotExist())
        .andExpect(
            header().string("Set-Cookie", containsString("refresh_token=token-de-renovacion")))
        .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
        .andExpect(header().string("Set-Cookie", containsString("Secure")))
        .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
        .andExpect(header().string("Set-Cookie", containsString("Path=/api/v1/auth")))
        .andExpect(header().string("Set-Cookie", containsString("Max-Age=604800")));
  }

  @ParameterizedTest
  @EnumSource(Rol.class)
  void esPublicoYUnaSesionDeCualquierRolNoCambiaNada(Rol rol) throws Exception {
    when(autenticacion.iniciarSesion(any(), any())).thenReturn(sesion());

    mvc.perform(login(CREDENCIALES).with(como(rol))).andExpect(status().isOk());
  }

  @Test
  void faltaElCorreoOLaContrasenaResponde400() throws Exception {
    mvc.perform(login("{\"correo\": \"\", \"contrasena\": \"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores.length()").value(2));
    mvc.perform(login("{}")).andExpect(status().isBadRequest());

    verifyNoInteractions(autenticacion);
  }

  @Test
  void laRespuestaDeCredencialesInvalidasEsUnProblemDetailsGenerico() throws Exception {
    when(autenticacion.iniciarSesion(any(), any()))
        .thenThrow(
            new NegocioException(
                HttpStatus.UNAUTHORIZED,
                "credenciales-invalidas",
                "Credenciales inválidas",
                "El correo o la contraseña no son correctos."));

    mvc.perform(login(CREDENCIALES))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value(endsWith("/credenciales-invalidas")))
        .andExpect(jsonPath("$.detail").value("El correo o la contraseña no son correctos."))
        .andExpect(header().doesNotExist("Set-Cookie"));
  }

  @Test
  void unaCuentaNoHabilitadaResponde403ConSuTipo() throws Exception {
    when(autenticacion.iniciarSesion(any(), any()))
        .thenThrow(
            new NegocioException(
                HttpStatus.FORBIDDEN, "cuenta-bloqueada", "Bloqueada", "Vuelve en 15 minutos."));

    mvc.perform(login(CREDENCIALES))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(endsWith("/cuenta-bloqueada")));
  }
}
