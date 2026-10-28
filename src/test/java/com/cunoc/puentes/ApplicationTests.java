package com.cunoc.puentes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationTests extends PruebaIntegracion {

  private static final String FOTOS = "$.paths['/api/v1/inspecciones/{inspeccionId}/fotos']";

  @Autowired MockMvc mvc;

  // DT-BE-12: el JSON de la entrega queda en target/openapi.json para el artefacto del CI
  @Test
  void exportaElOpenApiParaLaEntrega() throws Exception {
    String json =
        mvc.perform(get("/api/docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Files.createDirectories(Path.of("target"));
    Files.writeString(Path.of("target/openapi.json"), json);
  }

  // DT-BE-12: la documentación sale de los controladores y vive bajo /api/, que es lo que nginx
  // reenvía. Fuera de producción es pública; en prod exige sesión (SeguridadIntegracionTest)
  @Test
  void publicaLaDocumentacionEnApiDocs() throws Exception {
    mvc.perform(get("/api/docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.servers[0].url").value("/"))
        .andExpect(jsonPath(FOTOS + ".post.requestBody.content['multipart/form-data']").exists())
        .andExpect(jsonPath(FOTOS + ".post.responses['422']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/archivos/{id}'].delete.responses['409']").exists());
  }
}
