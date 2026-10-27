package com.cunoc.puentes.archivo;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import com.cunoc.puentes.usuario.Rol;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/** DT-CAL-03: cada endpoint contra cada rol. */
@WebMvcTest(ArchivoController.class)
@Import(SeguridadDePrueba.class)
class ArchivoControllerTest {

  private static final UUID USUARIO = UUID.fromString("0192f5a0-0000-7000-8000-000000000002");
  private static final String INSPECCION =
      "/api/v1/inspecciones/0192f5a0-0000-7000-8000-0000000000b1";

  @Autowired MockMvc mvc;

  @MockitoBean ArchivoService servicio;
  @MockitoBean DocumentoIdempotenciaService documentos;

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO", "CATEDRATICO"})
  void quienInspeccionaSubeYBorraArchivos(Rol rol) throws Exception {
    mvc.perform(subirFoto().with(como(rol, USUARIO))).andExpect(status().isCreated());
    mvc.perform(subirDocumento().with(como(rol, USUARIO))).andExpect(status().isCreated());
    mvc.perform(eliminar().with(como(rol, USUARIO))).andExpect(status().isNoContent());
  }

  @Test
  void elAdministradorNoSubeNiBorraArchivos() throws Exception {
    for (AbstractMockHttpServletRequestBuilder<?> peticion :
        List.of(subirFoto(), subirDocumento(), eliminar())) {
      mvc.perform(peticion.with(como(Rol.ADMINISTRADOR, USUARIO)))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void sinSesionResponde401() throws Exception {
    for (AbstractMockHttpServletRequestBuilder<?> peticion :
        List.of(subirFoto(), subirDocumento(), eliminar())) {
      mvc.perform(peticion.accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
    }
  }

  @Test
  void sinFechaDeCapturaResponde400ConElCampo() throws Exception {
    mvc.perform(
            multipart(INSPECCION + "/fotos")
                .file(new MockMultipartFile("foto", new byte[] {1}))
                .file(new MockMultipartFile("miniatura", new byte[] {1}))
                .param("id", "0192f5a0-0000-7000-8000-0000000000a1")
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, USUARIO)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores[0].campo").value("capturadaEn"));
  }

  private static AbstractMockHttpServletRequestBuilder<?> subirFoto() {
    return multipart(INSPECCION + "/fotos")
        .file(new MockMultipartFile("foto", new byte[] {1}))
        .file(new MockMultipartFile("miniatura", new byte[] {1}))
        .param("id", "0192f5a0-0000-7000-8000-0000000000a1")
        .param("elemento_ref", "subestructura.estribo_entrada")
        .param("latitud", "14.83")
        .param("longitud", "-91.52")
        .param("capturada_en", "2026-10-04T15:30:00Z")
        .with(csrf());
  }

  private static AbstractMockHttpServletRequestBuilder<?> subirDocumento() {
    return multipart(INSPECCION + "/documentos")
        .file(new MockMultipartFile("pdf", new byte[] {1}))
        .param("id", "0192f5a0-0000-7000-8000-0000000000a2")
        .with(csrf());
  }

  private static AbstractMockHttpServletRequestBuilder<?> eliminar() {
    return delete("/api/v1/archivos/0192f5a0-0000-7000-8000-0000000000a1").with(csrf());
  }
}
