package com.cunoc.puentes.formulario;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import com.cunoc.puentes.formulario.dto.CrearFormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.EditarFormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.FormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.PublicarFormularioVersionDTO;
import com.cunoc.puentes.usuario.Rol;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FormularioController.class)
@Import(SeguridadDePrueba.class)
class FormularioControllerTest {

  @Autowired private MockMvc mvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @MockitoBean private FormularioVersionService servicio;

  private final Map<String, Object> esquemaEjemplo =
      Map.of("type", "object", "properties", Map.of("campo", Map.of("type", "string")));

  @Test
  @DisplayName("Cualquier rol autenticado puede consultar la versión activa (RN-FRM-04)")
  void obtenerActivo_autenticado_retorna200() throws Exception {
    UUID id = UUID.randomUUID();
    FormularioVersionDTO dto =
        new FormularioVersionDTO(
            id,
            "V1.0.0",
            esquemaEjemplo,
            null,
            EstadoFormulario.ACTIVA,
            Instant.now(),
            Instant.now(),
            Instant.now());
    when(servicio.obtenerActivo()).thenReturn(dto);

    mvc.perform(get("/api/v1/formularios/activo").with(como(Rol.ESTUDIANTE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.codigo").value("V1.0.0"))
        .andExpect(jsonPath("$.estado").value("ACTIVA"));
  }

  @Test
  @DisplayName("Consulta de versión por código para auditoría histórica (RN-FRM-05)")
  void obtenerPorCodigo_autenticado_retorna200() throws Exception {
    UUID id = UUID.randomUUID();
    FormularioVersionDTO dto =
        new FormularioVersionDTO(
            id,
            "V1.0.0",
            esquemaEjemplo,
            null,
            EstadoFormulario.RETIRADA,
            Instant.now(),
            Instant.now(),
            Instant.now());
    when(servicio.obtenerPorCodigo("V1.0.0")).thenReturn(dto);

    mvc.perform(get("/api/v1/formularios/versiones/V1.0.0").with(como(Rol.CATEDRATICO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.codigo").value("V1.0.0"))
        .andExpect(jsonPath("$.estado").value("RETIRADA"));
  }

  @Test
  @DisplayName("Solo ADMINISTRADOR puede crear borradores de formulario")
  void crearBorrador_soloAdministradorPuedeCrear() throws Exception {
    CrearFormularioVersionDTO dto = new CrearFormularioVersionDTO("V2.0.0", esquemaEjemplo);
    UUID nuevoId = UUID.randomUUID();
    FormularioVersionDTO creado =
        new FormularioVersionDTO(
            nuevoId,
            "V2.0.0",
            esquemaEjemplo,
            null,
            EstadoFormulario.BORRADOR,
            null,
            Instant.now(),
            Instant.now());

    when(servicio.crearBorrador(any())).thenReturn(creado);

    // Con ESTUDIANTE debe fallar 403
    mvc.perform(
            post("/api/v1/formularios")
                .with(como(Rol.ESTUDIANTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
        .andExpect(status().isForbidden());

    // Con ADMINISTRADOR debe responder 201 Created
    mvc.perform(
            post("/api/v1/formularios")
                .with(como(Rol.ADMINISTRADOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.codigo").value("V2.0.0"))
        .andExpect(jsonPath("$.estado").value("BORRADOR"));
  }

  @Test
  @DisplayName("Solo ADMINISTRADOR puede publicar versiones de formulario (RN-FRM-04)")
  void publicar_soloAdministradorPuedePublicar() throws Exception {
    UUID id = UUID.randomUUID();
    PublicarFormularioVersionDTO dto = new PublicarFormularioVersionDTO(Map.of("c1", "c2"));
    FormularioVersionDTO publicada =
        new FormularioVersionDTO(
            id,
            "V2.0.0",
            esquemaEjemplo,
            Map.of("c1", "c2"),
            EstadoFormulario.ACTIVA,
            Instant.now(),
            Instant.now(),
            Instant.now());

    when(servicio.publicar(eq(id), any())).thenReturn(publicada);

    // Con CATEDRATICO debe fallar 403
    mvc.perform(
            post("/api/v1/formularios/" + id + "/publicar")
                .with(como(Rol.CATEDRATICO))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
        .andExpect(status().isForbidden());

    // Con ADMINISTRADOR debe responder 200 OK
    mvc.perform(
            post("/api/v1/formularios/" + id + "/publicar")
                .with(como(Rol.ADMINISTRADOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ACTIVA"));
  }
}
