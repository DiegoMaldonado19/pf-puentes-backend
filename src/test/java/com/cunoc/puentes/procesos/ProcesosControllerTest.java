package com.cunoc.puentes.procesos;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import com.cunoc.puentes.procesos.dto.EjecucionProcesoDTO;
import com.cunoc.puentes.usuario.Rol;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProcesosController.class)
@Import(SeguridadDePrueba.class)
class ProcesosControllerTest {

  private static final String RUTA = "/api/v1/procesos/alertas-inspeccion/ejecucion";

  @Autowired private MockMvc mvc;

  @MockitoBean private EjecucionManualProcesosService servicio;

  @Test
  @DisplayName("El administrador ejecuta el proceso y recibe su resultado (B10)")
  void ejecutar_administrador_retorna200() throws Exception {
    Instant ahora = Instant.parse("2026-11-11T12:00:00Z");
    when(servicio.ejecutar("alertas-inspeccion"))
        .thenReturn(new EjecucionProcesoDTO(UUID.randomUUID(), "alertas-inspeccion", ahora, ahora));

    mvc.perform(post(RUTA).with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nombre").value("alertas-inspeccion"))
        .andExpect(jsonPath("$.iniciado_en").value("2026-11-11T12:00:00Z"));
  }

  @Test
  @DisplayName("El catedrático no puede disparar procesos (403)")
  void ejecutar_catedratico_retorna403() throws Exception {
    mvc.perform(post(RUTA).with(como(Rol.CATEDRATICO))).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Si el proceso ya se ejecuta, la respuesta es 409")
  void ejecutar_enCurso_retorna409() throws Exception {
    when(servicio.ejecutar("alertas-inspeccion"))
        .thenThrow(
            new NegocioException(
                HttpStatus.CONFLICT,
                "proceso-en-ejecucion",
                "Proceso en ejecución",
                "El proceso ya se está ejecutando."));

    mvc.perform(post(RUTA).with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("proceso-en-ejecucion")));
  }
}
