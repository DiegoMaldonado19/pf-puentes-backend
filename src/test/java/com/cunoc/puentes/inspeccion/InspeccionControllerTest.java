package com.cunoc.puentes.inspeccion;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import com.cunoc.puentes.inspeccion.dto.CrearInspeccionDTO;
import com.cunoc.puentes.usuario.Rol;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

@WebMvcTest(InspeccionController.class)
@Import(SeguridadDePrueba.class)
class InspeccionControllerTest {

  private static final UUID USUARIO = UUID.fromString("0199c200-0000-7000-8000-000000000011");
  private static final String CREAR =
      """
      {"id":"0199c200-0000-7000-8000-0000000000aa",\
      "puente_id":"0199c200-0000-7000-8000-000000000001",\
      "fecha_inspeccion":"2026-10-24","dispositivo_id":"tablet"}
      """;

  @Autowired MockMvc mvc;
  @MockitoBean ConsultaInspeccionService consultas;
  @MockitoBean InicioInspeccionService inicio;
  @MockitoBean PrecargaInspeccionService precarga;
  @MockitoBean CapturaBorradorService captura;
  @MockitoBean EnvioInspeccionService envio;

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO", "CATEDRATICO"})
  void inspectoresConsultanPuentesEInician(Rol rol) throws Exception {
    org.mockito.Mockito.when(
            consultas.puentesInspeccionables(
                org.mockito.ArgumentMatchers.eq(USUARIO),
                org.mockito.ArgumentMatchers.eq(rol),
                org.mockito.ArgumentMatchers.any()))
        .thenReturn(Page.empty());
    mvc.perform(get("/api/v1/puentes-inspeccionables").with(como(rol, USUARIO)))
        .andExpect(status().isOk());
    org.mockito.Mockito.when(
            inicio.iniciar(
                org.mockito.ArgumentMatchers.eq(USUARIO),
                org.mockito.ArgumentMatchers.eq(rol),
                org.mockito.ArgumentMatchers.any(CrearInspeccionDTO.class)))
        .thenReturn(null);
    mvc.perform(
            post("/api/v1/inspecciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREAR)
                .with(csrf())
                .with(como(rol, USUARIO)))
        .andExpect(status().isCreated());
  }

  @Test
  void elAdministradorNoListaPuentesNiInicia() throws Exception {
    for (AbstractMockHttpServletRequestBuilder<?> peticion :
        List.of(
            get("/api/v1/puentes-inspeccionables"),
            post("/api/v1/inspecciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREAR)
                .with(csrf()))) {
      mvc.perform(peticion.with(como(Rol.ADMINISTRADOR, USUARIO)))
          .andExpect(status().isForbidden());
    }
  }

  @ParameterizedTest
  @EnumSource(Rol.class)
  void cuentaActivaConsultaListadoYDetalle(Rol rol) throws Exception {
    org.mockito.Mockito.when(
            consultas.listarMias(
                org.mockito.ArgumentMatchers.eq(USUARIO),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()))
        .thenReturn(Page.empty());
    mvc.perform(get("/api/v1/inspecciones").with(como(rol, USUARIO))).andExpect(status().isOk());
  }

  @Test
  void visitanteRecibe401() throws Exception {
    mvc.perform(get("/api/v1/inspecciones")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/puentes-inspeccionables")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/inspecciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREAR)
                .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void uuidQueNoEsV7Responde400EnElCampoId() throws Exception {
    mvc.perform(
            post("/api/v1/inspecciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"id":"550e8400-e29b-41d4-a716-446655440000",\
                    "puente_id":"0199c200-0000-7000-8000-000000000001",\
                    "fecha_inspeccion":"%s","dispositivo_id":"tablet"}
                    """
                        .formatted(LocalDate.now()))
                .with(csrf())
                .with(como(Rol.CATEDRATICO, USUARIO)))
        .andExpect(status().isBadRequest())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.errores[0].campo")
                .value("id"));
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO", "CATEDRATICO"})
  void inspectoresEnvianBorradores(Rol rol) throws Exception {
    mvc.perform(
            post("/api/v1/inspecciones/" + USUARIO + "/envio")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(csrf())
                .with(como(rol, USUARIO)))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO", "CATEDRATICO"})
  void inspectoresGuardanConfirmanYEliminanBorradores(Rol rol) throws Exception {
    mvc.perform(
            put("/api/v1/inspecciones/" + USUARIO)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"datos":{},"version":0}
                    """)
                .with(csrf())
                .with(como(rol, USUARIO)))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/inspecciones/" + USUARIO + "/confirmacion-datos-permanentes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"datos":{}}
                    """)
                .with(csrf())
                .with(como(rol, USUARIO)))
        .andExpect(status().isOk());
    mvc.perform(delete("/api/v1/inspecciones/" + USUARIO).with(csrf()).with(como(rol, USUARIO)))
        .andExpect(status().isNoContent());
  }

  @Test
  void elAdministradorNoEditaNiPreparaSalida() throws Exception {
    mvc.perform(
            put("/api/v1/inspecciones/" + USUARIO)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"datos":{},"version":0}
                    """)
                .with(csrf())
                .with(como(Rol.ADMINISTRADOR, USUARIO)))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/puentes/" + USUARIO + "/datos-permanentes")
                .with(como(Rol.ADMINISTRADOR, USUARIO)))
        .andExpect(status().isForbidden());
    mvc.perform(
            delete("/api/v1/inspecciones/" + USUARIO)
                .with(csrf())
                .with(como(Rol.ADMINISTRADOR, USUARIO)))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/inspecciones/" + USUARIO + "/envio")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(csrf())
                .with(como(Rol.ADMINISTRADOR, USUARIO)))
        .andExpect(status().isForbidden());
  }

  @Test
  void visitanteTampocoEditaElBorrador() throws Exception {
    mvc.perform(
            put("/api/v1/inspecciones/" + USUARIO)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"datos":{},"version":0}
                    """)
                .with(csrf()))
        .andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/v1/inspecciones/" + USUARIO).with(csrf()))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/inspecciones/" + USUARIO + "/envio")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(csrf()))
        .andExpect(status().isUnauthorized());
  }
}
