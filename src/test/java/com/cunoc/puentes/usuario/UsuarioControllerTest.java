package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** DT-CAL-03: {@code GET /usuarios/me} responde a cualquier rol autenticado y no al visitante. */
@WebMvcTest(UsuarioController.class)
@Import(SeguridadDePrueba.class)
class UsuarioControllerTest {

  private static final String ME = "/api/v1/usuarios/me";
  private static final UUID USUARIO = UUID.fromString("0192f5a0-0000-7000-8000-0000000000c1");

  @Autowired MockMvc mvc;

  @MockitoBean PerfilService perfiles;

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
  }
}
