package com.cunoc.puentes.inspeccion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class InspeccionOpenApiTest extends PruebaIntegracion {

  @Autowired MockMvc mvc;

  @Test
  void altaPublicaContratoYEsquemaDeReplay() throws Exception {
    mvc.perform(get("/api/docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/inspecciones'].post.responses['201'].content['application/json'].schema['$ref']")
                .value("#/components/schemas/InspeccionDTO"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/inspecciones'].post.parameters[?(@.name == 'Idempotency-Key')].in")
                .value(org.hamcrest.Matchers.hasItem("header")))
        .andExpect(
            jsonPath("$.paths['/api/v1/inspecciones'].post.description")
                .value(org.hamcrest.Matchers.containsString("2 segundos")));
  }

  @Test
  void guardarPublicaElEsquemaDeInspeccion() throws Exception {
    mvc.perform(get("/api/docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/inspecciones/{id}/envio'].post.responses['200'].content['application/json'].schema['$ref']")
                .value("#/components/schemas/InspeccionDTO"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/inspecciones/{id}/envio'].post.responses['422'].content['application/problem+json'].schema['$ref']")
                .value("#/components/schemas/ProblemDetail"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/inspecciones/{id}'].put.responses['200'].content['application/json'].schema['$ref']")
                .value("#/components/schemas/InspeccionDTO"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/inspecciones/{id}'].put.responses['413'].content['application/problem+json'].schema['$ref']")
                .value("#/components/schemas/ProblemDetail"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/inspecciones/{id}'].put.responses['422'].content['application/problem+json'].schema['$ref']")
                .value("#/components/schemas/ProblemDetail"));
  }
}
