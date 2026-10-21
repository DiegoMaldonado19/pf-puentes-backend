package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Los endpoints de recuperación son públicos: una sesión no cambia la respuesta. */
@WebMvcTest(RecuperacionContrasenaController.class)
@Import({SeguridadDePrueba.class, CookieRenovacion.class})
class RecuperacionContrasenaControllerTest {

  private static final String RECUPERAR = "/api/v1/auth/recuperar-contrasena";
  private static final String RESTABLECER = "/api/v1/auth/restablecer-contrasena";
  private static final String TOKEN = "token-de-prueba-123";
  private static final String CONTRASENA = "Puentes2026ok";

  @Autowired MockMvc mvc;

  @MockitoBean RecuperacionContrasenaService recuperacion;

  private static MockHttpServletRequestBuilder json(String ruta, String cuerpo) {
    return post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
  }

  @Test
  void sinSesionSolicitaYRestablece() throws Exception {
    mvc.perform(json(RECUPERAR, "{\"correo\": \"  ANA@Correo.com \"}"))
        .andExpect(status().isAccepted());
    mvc.perform(
            json(
                RESTABLECER,
                """
                {"token": "%s", "contrasena": "%s"}
                """
                    .formatted(TOKEN, CONTRASENA)))
        .andExpect(status().isNoContent())
        .andExpect(header().string("Set-Cookie", containsString("refresh_token=;")))
        .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

    verify(recuperacion).solicitar("ana@correo.com");
    verify(recuperacion).restablecer(TOKEN, CONTRASENA);
  }

  @ParameterizedTest
  @EnumSource(Rol.class)
  void unaSesionNoCambiaLaRespuesta(Rol rol) throws Exception {
    mvc.perform(json(RECUPERAR, "{\"correo\": \"ana@correo.com\"}").with(como(rol)))
        .andExpect(status().isAccepted());
  }

  @Test
  void unCuerpoInvalidoResponde400() throws Exception {
    mvc.perform(json(RECUPERAR, "{\"correo\": \"no-es-correo\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(json(RESTABLECER, "{\"token\": \"abc\", \"contrasena\": \"corta1\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(json(RESTABLECER, "{}")).andExpect(status().isBadRequest());

    verifyNoInteractions(recuperacion);
  }
}
