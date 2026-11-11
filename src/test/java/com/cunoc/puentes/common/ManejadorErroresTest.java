package com.cunoc.puentes.common;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
  void validacionNombraElCampoEnSnakeCaseYDaElDetalleEnEspanol() throws Exception {
    mvc.perform(
            post("/prueba/camel")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contrasenaNueva\": \"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Datos no válidos"))
        .andExpect(jsonPath("$.detail").value("Hay campos con valores no válidos."))
        .andExpect(jsonPath("$.errores[0].campo").value("contrasena_nueva"))
        .andExpect(jsonPath("$.errores[0].mensaje").value("Es obligatorio."));
  }

  @Test
  void jsonIlegibleRespondeEnEspanolSinDetallesDelParser() throws Exception {
    mvc.perform(
            post("/prueba/validacion")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{nombre"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail").value("El cuerpo de la solicitud no es JSON válido o está vacío."))
        .andExpect(jsonPath("$.errores").doesNotExist());
  }

  @Test
  void valorDeEnumNoPermitidoNombraElCampoYLasOpciones() throws Exception {
    mvc.perform(
            post("/prueba/enum")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\": \"DIOS\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Hay valores con un tipo de dato no válido."))
        .andExpect(jsonPath("$.errores[0].campo").value("rol"))
        .andExpect(
            jsonPath("$.errores[0].mensaje")
                .value("Valor no permitido. Usa uno de: CATEDRATICO, ESTUDIANTE."));
  }

  @Test
  void parametroDeTipoIncorrectoNombraElParametro() throws Exception {
    mvc.perform(get("/prueba/tipo/abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores[0].campo").value("id"))
        .andExpect(jsonPath("$.errores[0].mensaje").value("Tipo de dato no válido."));
  }

  @Test
  void violacionDeIntegridadResponde409SinExponerLaRestriccion() throws Exception {
    mvc.perform(get("/prueba/integridad"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Conflicto con los datos"))
        .andExpect(
            jsonPath("$.detail")
                .value("Los datos chocan con registros existentes o con referencias en uso."));
  }

  @Test
  void traduceLosMensajesPorDefectoDeValidacion() {
    assertThat(ManejadorErrores.mensajeDeValidacion("size must be between 1 and 200"))
        .isEqualTo("Debe tener entre 1 y 200 caracteres.");
    assertThat(ManejadorErrores.mensajeDeValidacion("must be less than or equal to 100"))
        .isEqualTo("Debe ser menor o igual a 100.");
    assertThat(ManejadorErrores.mensajeDeValidacion("must be greater than or equal to 0"))
        .isEqualTo("Debe ser mayor o igual a 0.");
    assertThat(ManejadorErrores.mensajeDeValidacion("must be a well-formed email address"))
        .isEqualTo("El correo no tiene un formato válido.");
    assertThat(ManejadorErrores.mensajeDeValidacion(null)).isEqualTo("Es obligatorio.");
    assertThat(ManejadorErrores.mensajeDeValidacion("Mensaje propio en español."))
        .isEqualTo("Mensaje propio en español.");
  }

  @Test
  void convierteLasRutasDeCamelCaseASnakeCase() {
    assertThat(ManejadorErrores.snakeCase("subestructura.estriboEntrada"))
        .isEqualTo("subestructura.estribo_entrada");
    assertThat(ManejadorErrores.snakeCase("capturadaEn")).isEqualTo("capturada_en");
    assertThat(ManejadorErrores.snakeCase("ya_snake.campo")).isEqualTo("ya_snake.campo");
    assertThat(ManejadorErrores.snakeCase("URLFoto")).isEqualTo("url_foto");
    assertThat(ManejadorErrores.snakeCase("")).isEqualTo("");
    assertThat(ManejadorErrores.snakeCase(null)).isNull();
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

    record Cuenta(@NotBlank String contrasenaNueva) {}

    enum Perfil {
      CATEDRATICO,
      ESTUDIANTE
    }

    record Registro(Perfil rol) {}

    @PostMapping("/prueba/camel")
    void camel(@Valid @RequestBody Cuenta cuenta) {}

    @PostMapping("/prueba/enum")
    void enumerado(@RequestBody Registro registro) {}

    @GetMapping("/prueba/tipo/{id}")
    void tipo(@PathVariable int id) {}

    @GetMapping("/prueba/integridad")
    void integridad() {
      throw new DataIntegrityViolationException(
          "duplicate key value violates unique constraint \"uq_usuario_correo\"");
    }

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
