package com.cunoc.puentes.common.auditoria;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.auditoria.dto.AuditoriaDTO;
import com.cunoc.puentes.common.auditoria.dto.PaginaAuditoriaDTO;
import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import com.cunoc.puentes.usuario.Rol;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** DT-CAL-03: solo el Administrador consulta la bitácora. */
@WebMvcTest(AuditoriaController.class)
@Import(SeguridadDePrueba.class)
class AuditoriaControllerTest {

  private static final String BASE = "/api/v1/auditoria";
  private static final UUID ADMIN = UUID.fromString("0192f5a0-0000-7000-8000-0000000000a1");
  private static final UUID FILA = UUID.fromString("0192f5a0-0000-7000-8000-0000000000d1");
  private static final UUID ENTIDAD = UUID.fromString("0192f5a0-0000-7000-8000-0000000000c1");

  @Autowired MockMvc mvc;

  @MockitoBean ConsultaAuditoriaService consulta;

  @Test
  void sinSesionResponde401() throws Exception {
    mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO", "CATEDRATICO"})
  void quienNoEsAdministradorRecibe403(Rol rol) throws Exception {
    mvc.perform(get(BASE).with(como(rol)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  void elAdministradorListaConFiltros() throws Exception {
    Instant creado = Instant.parse("2026-10-20T15:00:00Z");
    when(consulta.listar(
            eq("usuario"), eq(ENTIDAD), eq(ADMIN), eq("DAR_DE_BAJA"), isNull(), isNull(), any()))
        .thenReturn(
            new PaginaAuditoriaDTO(
                List.of(
                    new AuditoriaDTO(
                        FILA,
                        ADMIN,
                        "DAR_DE_BAJA",
                        "usuario",
                        ENTIDAD,
                        Map.of("estado", "ACTIVO"),
                        Map.of("estado", "INACTIVO"),
                        "Ya no pertenece al curso",
                        "127.0.0.1",
                        UUID.randomUUID(),
                        creado)),
                0,
                20,
                1,
                1));

    mvc.perform(
            get(BASE)
                .param("entidad", "usuario")
                .param("entidad_id", ENTIDAD.toString())
                .param("usuario_id", ADMIN.toString())
                .param("accion", "DAR_DE_BAJA")
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1))
        .andExpect(jsonPath("$.content[0].accion").value("DAR_DE_BAJA"))
        .andExpect(jsonPath("$.content[0].usuario_id").value(ADMIN.toString()))
        .andExpect(jsonPath("$.content[0].entidad_id").value(ENTIDAD.toString()))
        .andExpect(jsonPath("$.content[0].contrasena_hash").doesNotExist())
        .andExpect(jsonPath("$.content[0].token_hash").doesNotExist());

    verify(consulta)
        .listar(
            eq("usuario"), eq(ENTIDAD), eq(ADMIN), eq("DAR_DE_BAJA"), isNull(), isNull(), any());
  }
}
