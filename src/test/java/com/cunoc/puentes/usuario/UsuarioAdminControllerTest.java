package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import com.cunoc.puentes.usuario.dto.UsuarioAdminDTO;
import java.time.Instant;
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

/** DT-CAL-03: solo el Administrador verifica o rechaza un colegiado. */
@WebMvcTest(UsuarioAdminController.class)
@Import(SeguridadDePrueba.class)
class UsuarioAdminControllerTest {

  private static final UUID ADMIN = UUID.fromString("0192f5a0-0000-7000-8000-0000000000a1");
  private static final UUID PROFESIONAL = UUID.fromString("0192f5a0-0000-7000-8000-0000000000c1");
  private static final String VERIFICAR =
      "/api/v1/usuarios/" + PROFESIONAL + "/colegiado/verificacion";
  private static final String RECHAZAR = "/api/v1/usuarios/" + PROFESIONAL + "/colegiado/rechazo";

  @Autowired MockMvc mvc;

  @MockitoBean VerificacionColegiadoService colegiados;

  @Test
  void sinSesionResponde401() throws Exception {
    mvc.perform(post(VERIFICAR)).andExpect(status().isUnauthorized());
    mvc.perform(
            post(RECHAZAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Colegiado ilegible\"}"))
        .andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO", "CATEDRATICO"})
  void quienNoEsAdministradorRecibe403(Rol rol) throws Exception {
    mvc.perform(post(VERIFICAR).with(como(rol)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
    mvc.perform(
            post(RECHAZAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Colegiado ilegible\"}")
                .with(como(rol)))
        .andExpect(status().isForbidden());
  }

  @Test
  void elAdministradorApruebaYRechaza() throws Exception {
    when(colegiados.aprobar(ADMIN, PROFESIONAL)).thenReturn(dto(EstadoUsuario.ACTIVO, null));
    when(colegiados.rechazar(ADMIN, PROFESIONAL, "Colegiado ilegible"))
        .thenReturn(dto(EstadoUsuario.INACTIVO, "Colegiado ilegible"));

    mvc.perform(post(VERIFICAR).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ACTIVO"))
        .andExpect(jsonPath("$.contrasena_hash").doesNotExist());

    mvc.perform(
            post(RECHAZAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"  Colegiado ilegible  \"}")
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("INACTIVO"));

    verify(colegiados).aprobar(ADMIN, PROFESIONAL);
    verify(colegiados).rechazar(ADMIN, PROFESIONAL, "Colegiado ilegible");
  }

  @Test
  void unRechazoSinMotivoResponde400() throws Exception {
    mvc.perform(
            post(RECHAZAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(colegiados);
  }

  private static UsuarioAdminDTO dto(EstadoUsuario estado, String motivoBaja) {
    return new UsuarioAdminDTO(
        PROFESIONAL,
        "Ana Profesional",
        "ana@correo.com",
        Rol.PROFESIONAL_EXTERNO,
        estado,
        true,
        "CIG-7788",
        estado == EstadoUsuario.ACTIVO ? Instant.parse("2026-10-20T15:00:00Z") : null,
        estado == EstadoUsuario.INACTIVO ? Instant.parse("2026-10-20T15:00:00Z") : null,
        motivoBaja,
        Instant.parse("2026-10-20T14:00:00Z"));
  }
}
