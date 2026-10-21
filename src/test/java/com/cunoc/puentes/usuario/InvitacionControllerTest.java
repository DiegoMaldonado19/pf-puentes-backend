package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import com.cunoc.puentes.usuario.dto.InvitacionDTO;
import com.cunoc.puentes.usuario.dto.PaginaDTO;
import java.time.Instant;
import java.util.List;
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

/** DT-CAL-03: solo el Administrador invita, lista y revoca. */
@WebMvcTest(InvitacionController.class)
@Import(SeguridadDePrueba.class)
class InvitacionControllerTest {

  private static final String BASE = "/api/v1/invitaciones";
  private static final UUID ADMIN = UUID.fromString("0192f5a0-0000-7000-8000-0000000000a1");
  private static final UUID INVITACION = UUID.fromString("0192f5a0-0000-7000-8000-0000000000b1");

  @Autowired MockMvc mvc;

  @MockitoBean InvitacionService invitaciones;

  @Test
  void sinSesionResponde401() throws Exception {
    mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    mvc.perform(
            post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"prof@correo.com\",\"rol\":\"CATEDRATICO\"}"))
        .andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO", "CATEDRATICO"})
  void quienNoEsAdministradorRecibe403(Rol rol) throws Exception {
    mvc.perform(get(BASE).with(como(rol)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
    mvc.perform(
            post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"prof@correo.com\",\"rol\":\"CATEDRATICO\"}")
                .with(como(rol)))
        .andExpect(status().isForbidden());
  }

  @Test
  void elAdministradorCreaListaYRevoca() throws Exception {
    when(invitaciones.crear(eq(ADMIN), any())).thenReturn(dto());
    when(invitaciones.listar(isNull(), any()))
        .thenReturn(new PaginaDTO<>(List.of(dto()), 0, 20, 1, 1));
    when(invitaciones.revocar(ADMIN, INVITACION)).thenReturn(dto());

    mvc.perform(
            post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"  Prof@Correo.COM \",\"rol\":\"CATEDRATICO\"}")
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.correo").value("prof@correo.com"))
        .andExpect(jsonPath("$.token").doesNotExist())
        .andExpect(jsonPath("$.token_hash").doesNotExist());

    mvc.perform(get(BASE).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1));

    mvc.perform(post(BASE + "/" + INVITACION + "/revocacion").with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk());

    verify(invitaciones).revocar(ADMIN, INVITACION);
  }

  @Test
  void unCuerpoInvalidoResponde400SinLlamarAlServicio() throws Exception {
    mvc.perform(
            post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"no-es-correo\"}")
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(invitaciones);
  }

  private static InvitacionDTO dto() {
    return new InvitacionDTO(
        INVITACION,
        "prof@correo.com",
        Rol.CATEDRATICO,
        EstadoInvitacion.ABIERTA,
        Instant.parse("2026-10-27T15:00:00Z"),
        Instant.parse("2026-10-20T15:00:00Z"),
        ADMIN,
        "Admin",
        null);
  }
}
