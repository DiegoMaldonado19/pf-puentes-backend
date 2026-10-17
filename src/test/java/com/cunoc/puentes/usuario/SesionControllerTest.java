package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import com.cunoc.puentes.usuario.dto.SesionDTO;
import com.cunoc.puentes.usuario.dto.UsuarioSesionDTO;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** El contrato HTTP de renovar y cerrar sesión: cookie de entrada, cookie borrada y errores. */
@WebMvcTest(SesionController.class)
@Import({SeguridadDePrueba.class, CookieRenovacion.class})
class SesionControllerTest {

  private static final String RENOVAR = "/api/v1/auth/renovar";
  private static final String CERRAR = "/api/v1/auth/cerrar-sesion";

  @Autowired MockMvc mvc;

  @MockitoBean SesionService sesiones;

  private static SesionDTO sesion() {
    return new SesionDTO(
        "jwt.nuevo",
        SesionDTO.BEARER,
        900,
        new UsuarioSesionDTO(UUID.randomUUID(), "Carlos Ruiz", Rol.CATEDRATICO));
  }

  @Test
  void renuevaConLaCookieYNoDevuelveElTokenDeRenovacion() throws Exception {
    when(sesiones.renovar("token-de-renovacion")).thenReturn(sesion());

    mvc.perform(post(RENOVAR).cookie(new Cookie("refresh_token", "token-de-renovacion")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").value("jwt.nuevo"))
        .andExpect(jsonPath("$.expires_in").value(900))
        .andExpect(jsonPath("$.usuario.rol").value("CATEDRATICO"))
        .andExpect(jsonPath("$.refresh_token").doesNotExist())
        .andExpect(header().doesNotExist("Set-Cookie"));
  }

  @ParameterizedTest
  @EnumSource(Rol.class)
  void esPublicoYUnaSesionDeCualquierRolNoCambiaNada(Rol rol) throws Exception {
    when(sesiones.renovar("t")).thenReturn(sesion());

    mvc.perform(post(RENOVAR).cookie(new Cookie("refresh_token", "t")).with(como(rol)))
        .andExpect(status().isOk());
  }

  @Test
  void unaSesionInvalidaResponde401YOrdenaBorrarLaCookie() throws Exception {
    when(sesiones.renovar(any())).thenThrow(new SesionInvalidaException());

    mvc.perform(post(RENOVAR).cookie(new Cookie("refresh_token", "x")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value(endsWith("/sesion-invalida")))
        .andExpect(header().string("Content-Type", containsString("application/problem+json")))
        .andExpect(header().string("Set-Cookie", containsString("refresh_token=;")))
        .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
        .andExpect(header().string("Set-Cookie", containsString("Path=/api/v1/auth")))
        .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
  }

  @Test
  void sinCookieLlamaAlServicioConNuloYResponde401() throws Exception {
    when(sesiones.renovar(null)).thenThrow(new SesionInvalidaException());

    mvc.perform(post(RENOVAR)).andExpect(status().isUnauthorized());
  }

  @Test
  void cerrarSesionResponde204BorraLaCookieYRevocaElToken() throws Exception {
    mvc.perform(post(CERRAR).cookie(new Cookie("refresh_token", "mi-token")))
        .andExpect(status().isNoContent())
        .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

    verify(sesiones).cerrar("mi-token");
  }

  @Test
  void cerrarSesionSinCookieTambienResponde204() throws Exception {
    mvc.perform(post(CERRAR)).andExpect(status().isNoContent());

    verify(sesiones).cerrar(null);
  }
}
