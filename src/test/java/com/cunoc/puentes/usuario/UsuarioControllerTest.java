package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import com.cunoc.puentes.usuario.dto.PerfilDTO;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** DT-CAL-03: perfil y cambio de contraseña propios, para cualquier rol autenticado. */
@WebMvcTest(UsuarioController.class)
@Import({SeguridadDePrueba.class, CookieRenovacion.class})
class UsuarioControllerTest {

  private static final String ME = "/api/v1/usuarios/me";
  private static final String CONTRASENA = ME + "/contrasena";
  private static final UUID USUARIO = UUID.fromString("0192f5a0-0000-7000-8000-0000000000c1");
  private static final String CAMBIO =
      """
      {"contrasena_actual": "Puentes2026ok", "contrasena_nueva": "Puentes2027ok"}
      """;

  @Autowired MockMvc mvc;

  @MockitoBean PerfilService perfiles;
  @MockitoBean CambioContrasenaService contrasenas;

  @ParameterizedTest
  @EnumSource(Rol.class)
  void cualquierRolAutenticadoVeSuPerfil(Rol rol) throws Exception {
    when(perfiles.obtener(USUARIO))
        .thenReturn(
            new PerfilDTO(USUARIO, "Carlos Ruiz", "carlos@correo.com", rol, EstadoUsuario.ACTIVO));

    mvc.perform(get(ME).with(como(rol, USUARIO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(USUARIO.toString()))
        .andExpect(jsonPath("$.nombre").value("Carlos Ruiz"))
        .andExpect(jsonPath("$.correo").value("carlos@correo.com"))
        .andExpect(jsonPath("$.rol").value(rol.name()))
        .andExpect(jsonPath("$.estado").value("ACTIVO"))
        .andExpect(jsonPath("$.contrasena_hash").doesNotExist())
        .andExpect(jsonPath("$.contrasenaHash").doesNotExist());
  }

  @Test
  void elIdSaleDelTokenNuncaDeLaPeticion() throws Exception {
    UUID otro = UUID.fromString("0192f5a0-0000-7000-8000-0000000000c2");
    when(perfiles.obtener(USUARIO))
        .thenReturn(
            new PerfilDTO(
                USUARIO, "Carlos", "carlos@correo.com", Rol.ESTUDIANTE, EstadoUsuario.ACTIVO));

    mvc.perform(get(ME).param("id", otro.toString()).with(como(Rol.ESTUDIANTE, USUARIO)))
        .andExpect(status().isOk());

    verify(perfiles).obtener(USUARIO);
  }

  @Test
  void unVisitanteRecibe401() throws Exception {
    mvc.perform(get(ME)).andExpect(status().isUnauthorized());
    mvc.perform(put(CONTRASENA).contentType(MediaType.APPLICATION_JSON).content(CAMBIO))
        .andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @EnumSource(Rol.class)
  void cualquierRolAutenticadoCambiaSuContrasenaYBorraLaCookie(Rol rol) throws Exception {
    mvc.perform(
            put(CONTRASENA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CAMBIO)
                .with(como(rol, USUARIO)))
        .andExpect(status().isNoContent())
        .andExpect(header().string("Set-Cookie", containsString("refresh_token=;")))
        .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

    verify(contrasenas).cambiar(USUARIO, "Puentes2026ok", "Puentes2027ok");
  }

  @Test
  void elCambioUsaElIdDelTokenAunqueVengaOtroEnLaRuta() throws Exception {
    UUID otro = UUID.fromString("0192f5a0-0000-7000-8000-0000000000c2");

    mvc.perform(
            put(CONTRASENA)
                .param("id", otro.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(CAMBIO)
                .with(como(Rol.ESTUDIANTE, USUARIO)))
        .andExpect(status().isNoContent());

    verify(contrasenas).cambiar(USUARIO, "Puentes2026ok", "Puentes2027ok");
  }

  @Test
  void unaContrasenaNuevaInvalidaOSinCamposResponde400() throws Exception {
    mvc.perform(
            put(CONTRASENA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"contrasena_actual\":\"Puentes2026ok\",\"contrasena_nueva\":\"corta1\"}")
                .with(como(Rol.ESTUDIANTE, USUARIO)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            put(CONTRASENA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(como(Rol.ESTUDIANTE, USUARIO)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(contrasenas);
  }
}
