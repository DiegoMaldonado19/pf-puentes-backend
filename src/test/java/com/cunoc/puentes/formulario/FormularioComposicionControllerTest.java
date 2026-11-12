package com.cunoc.puentes.formulario;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.ErrorCampo;
import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import com.cunoc.puentes.formulario.dto.EsquemaBaseDTO;
import com.cunoc.puentes.formulario.dto.ResultadoValidacionFormularioDTO;
import com.cunoc.puentes.formulario.dto.SeccionFormularioDTO;
import com.cunoc.puentes.usuario.Rol;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** B2: endpoints del constructor de formularios, solo para administrador. */
@WebMvcTest(FormularioController.class)
@Import(SeguridadDePrueba.class)
class FormularioComposicionControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private FormularioVersionService servicio;

  @Test
  @DisplayName("El administrador obtiene el esquema base y las secciones admitidas")
  void esquemaBase_administrador_retorna200() throws Exception {
    when(servicio.esquemaBase())
        .thenReturn(
            new EsquemaBaseDTO(
                Map.of("type", "object"),
                List.of(new SeccionFormularioDTO("otros", "Sección 8", true))));

    mvc.perform(get("/api/v1/formularios/base").with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.esquema.type").value("object"))
        .andExpect(jsonPath("$.secciones[0].clave").value("otros"))
        .andExpect(jsonPath("$.secciones[0].admite_campos_extra").value(true));
  }

  @Test
  @DisplayName("El catedrático no ve el constructor de formularios (403)")
  void esquemaBase_catedratico_retorna403() throws Exception {
    mvc.perform(get("/api/v1/formularios/base").with(como(Rol.CATEDRATICO)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("La validación en seco responde 200 con los errores por campo, sin guardar")
  void validacion_administrador_retornaErrores() throws Exception {
    when(servicio.validarComposicion(any()))
        .thenReturn(
            new ResultadoValidacionFormularioDTO(
                false, List.of(new ErrorCampo("campos_extra[0].clave", "Clave no válida."))));

    mvc.perform(
            post("/api/v1/formularios/validacion")
                .with(como(Rol.ADMINISTRADOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"campos_extra": [{"seccion": "otros", "clave": "X", "titulo": "T",
                      "tipo": "texto"}]}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valido").value(false))
        .andExpect(jsonPath("$.errores[0].campo").value("campos_extra[0].clave"));
  }

  @Test
  @DisplayName("Un estudiante no puede validar composiciones (403)")
  void validacion_estudiante_retorna403() throws Exception {
    mvc.perform(
            post("/api/v1/formularios/validacion")
                .with(como(Rol.ESTUDIANTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }
}
