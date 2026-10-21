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
import com.cunoc.puentes.usuario.dto.PaginaDTO;
import com.cunoc.puentes.usuario.dto.UsuarioAdminDTO;
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

/** DT-CAL-03: solo el Administrador administra cuentas y verifica colegiados. */
@WebMvcTest(UsuarioAdminController.class)
@Import(SeguridadDePrueba.class)
class UsuarioAdminControllerTest {

  private static final UUID ADMIN = UUID.fromString("0192f5a0-0000-7000-8000-0000000000a1");
  private static final UUID PROFESIONAL = UUID.fromString("0192f5a0-0000-7000-8000-0000000000c1");
  private static final String BASE = "/api/v1/usuarios";
  private static final String VERIFICAR = BASE + "/" + PROFESIONAL + "/colegiado/verificacion";
  private static final String RECHAZAR = BASE + "/" + PROFESIONAL + "/colegiado/rechazo";
  private static final String BAJA = BASE + "/" + PROFESIONAL + "/baja";
  private static final String REACTIVAR = BASE + "/" + PROFESIONAL + "/reactivacion";

  @Autowired MockMvc mvc;

  @MockitoBean VerificacionColegiadoService colegiados;
  @MockitoBean AdministracionUsuariosService cuentas;

  @Test
  void sinSesionResponde401() throws Exception {
    mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    mvc.perform(get(BASE + "/" + PROFESIONAL)).andExpect(status().isUnauthorized());
    mvc.perform(post(VERIFICAR)).andExpect(status().isUnauthorized());
    mvc.perform(
            post(RECHAZAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Colegiado ilegible\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post(BAJA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Baja de prueba\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post(REACTIVAR)).andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO", "CATEDRATICO"})
  void quienNoEsAdministradorRecibe403(Rol rol) throws Exception {
    mvc.perform(get(BASE).with(como(rol)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
    mvc.perform(get(BASE + "/" + PROFESIONAL).with(como(rol))).andExpect(status().isForbidden());
    mvc.perform(post(VERIFICAR).with(como(rol))).andExpect(status().isForbidden());
    mvc.perform(
            post(RECHAZAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Colegiado ilegible\"}")
                .with(como(rol)))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(BAJA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Baja de prueba\"}")
                .with(como(rol)))
        .andExpect(status().isForbidden());
    mvc.perform(post(REACTIVAR).with(como(rol))).andExpect(status().isForbidden());
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
  void elAdministradorListaConsultaDaDeBajaYReactiva() throws Exception {
    when(cuentas.listar(eq(Rol.ESTUDIANTE), isNull(), eq("ana"), any()))
        .thenReturn(new PaginaDTO<>(List.of(dto(EstadoUsuario.ACTIVO, null)), 0, 20, 1, 1));
    when(cuentas.obtener(PROFESIONAL)).thenReturn(dto(EstadoUsuario.ACTIVO, null));
    when(cuentas.darDeBaja(ADMIN, PROFESIONAL, "Ya no pertenece al curso"))
        .thenReturn(dto(EstadoUsuario.INACTIVO, "Ya no pertenece al curso"));
    when(cuentas.reactivar(PROFESIONAL)).thenReturn(dto(EstadoUsuario.ACTIVO, null));

    mvc.perform(
            get(BASE)
                .param("rol", "ESTUDIANTE")
                .param("q", "ana")
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1))
        .andExpect(jsonPath("$.content[0].contrasena_hash").doesNotExist());

    mvc.perform(get(BASE + "/" + PROFESIONAL).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(PROFESIONAL.toString()));

    mvc.perform(
            post(BAJA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"  Ya no pertenece al curso  \"}")
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("INACTIVO"));

    mvc.perform(post(REACTIVAR).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ACTIVO"));

    verify(cuentas).darDeBaja(ADMIN, PROFESIONAL, "Ya no pertenece al curso");
    verify(cuentas).reactivar(PROFESIONAL);
  }

  @Test
  void unRechazoOBajaSinMotivoResponde400() throws Exception {
    mvc.perform(
            post(RECHAZAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(BAJA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(colegiados, cuentas);
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
