package com.cunoc.puentes.common;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(useDefaultFilters = false)
@Import({ManejadorErrores.class, ManejadorErroresTest.ControladorPrueba.class})
@WithMockUser
class ManejadorErroresTest {

  @Autowired MockMvc mvc;

  @Test
  void negocioRespondeConTipoTituloDetalleInstanciaYErrores() throws Exception {
    mvc.perform(get("/prueba/negocio"))
        .andExpect(status().is(422))
        .andExpect(
            jsonPath("$.type")
                .value("https://sgp.cunoc.usac.edu.gt/errores/inspeccion-no-publicable"))
        .andExpect(jsonPath("$.title").value("La inspección no puede enviarse a revisión"))
        .andExpect(jsonPath("$.detail").value("Faltan campos obligatorios."))
        .andExpect(jsonPath("$.instance").value("/prueba/negocio"))
        .andExpect(jsonPath("$.errores[0].campo").value("subestructura.material"))
        .andExpect(jsonPath("$.errores[0].mensaje").value("Requerido"));
  }

  @Test
  void validacionResponde400ConLosCamposInvalidos() throws Exception {
    mvc.perform(
            post("/prueba/validacion")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\": \"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores[0].campo").value("nombre"));
  }

  @Test
  void errorInesperadoRespondeSinDetallesInternos() throws Exception {
    mvc.perform(get("/prueba/inesperado"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado."));
  }

  @Test
  void accesoDenegadoLoResuelveSpringSecurity() throws Exception {
    mvc.perform(get("/prueba/denegado")).andExpect(status().isForbidden());
  }

  @RestController
  static class ControladorPrueba {

    record Datos(@NotBlank String nombre) {}

    @GetMapping("/prueba/negocio")
    void negocio() {
      throw new NegocioException(
              HttpStatus.UNPROCESSABLE_CONTENT,
              "inspeccion-no-publicable",
              "La inspección no puede enviarse a revisión",
              "Faltan campos obligatorios.")
          .conErrores(List.of(new ErrorCampo("subestructura.material", "Requerido")));
    }

    @PostMapping("/prueba/validacion")
    void validacion(@Valid @RequestBody Datos datos) {}

    @GetMapping("/prueba/inesperado")
    void inesperado() {
      throw new IllegalStateException("detalle interno que no debe salir");
    }

    @GetMapping("/prueba/denegado")
    void denegado() {
      throw new AccessDeniedException("sin permiso");
    }
  }
}
