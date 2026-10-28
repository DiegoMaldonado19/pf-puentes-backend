package com.cunoc.puentes.inspeccion;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.archivo.AlmacenamientoArchivos;
import com.cunoc.puentes.usuario.Rol;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Flujo de punta a punta contra el SIECA V1.0.0 real, no contra el formulario provisional. */
@SpringBootTest(properties = "sgp.pruebas.formulario-real=true")
@AutoConfigureMockMvc
@Sql("/inspeccion/sieca.sql")
@Sql(
    scripts = "/inspeccion/sieca-limpiar.sql",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class InspeccionSiecaTest extends PruebaIntegracion {

  private static final UUID ESTUDIANTE = UUID.fromString("0199c300-0000-7000-8000-000000000001");
  private static final UUID CATEDRATICO = UUID.fromString("0199c300-0000-7000-8000-000000000002");
  private static final UUID PUENTE = UUID.fromString("0199c300-0000-7000-8000-000000000010");
  private static final UUID BORRADOR = UUID.fromString("0199c300-0000-7000-8000-0000000000b1");
  private static final String CODIGO_PUENTE = "GT-09-0901-9301";
  private static final String BASE = "/api/v1/inspecciones";
  private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0};
  private static final String DATOS_GENERALES =
      """
      {"datos_generales":{"longitud_total":30,"numero_tramos":1,"ancho_rodadura":7.2}}
      """;
  private static final String GRUPOS_SIN_DANOS =
      """
      ["estribos","pilas","cimentacion","vigas","losa","juntas","apoyos",
       "seguridad_vial","aceras","drenajes","hidraulica"]
      """;

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired AlmacenamientoArchivos almacenamiento;

  @AfterEach
  void borrarObjetosDeFotos() {
    jdbc.sql(
            """
            select clave from archivo
            where inspeccion_id in (select id from inspeccion where puente_id = :puente)
            union all
            select clave_miniatura from archivo
            where clave_miniatura is not null
              and inspeccion_id in (select id from inspeccion where puente_id = :puente)
            """)
        .param("puente", PUENTE)
        .query(String.class)
        .list()
        .forEach(almacenamiento::eliminar);
  }

  @Test
  void estudianteEnviaSuInspeccionDePuntaAPunta() throws Exception {
    iniciarBorrador(ESTUDIANTE, Rol.ESTUDIANTE);
    guardarAvance(ESTUDIANTE, Rol.ESTUDIANTE, DATOS_GENERALES);
    subirFotos(ESTUDIANTE, Rol.ESTUDIANTE);
    enviar(ESTUDIANTE, Rol.ESTUDIANTE)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ENVIADA"))
        .andExpect(jsonPath("$.indice_condicion").doesNotExist());
  }

  @Test
  void elEnvioConservaLaIdentificacionDelInventario() throws Exception {
    iniciarBorrador(ESTUDIANTE, Rol.ESTUDIANTE);
    guardarAvance(
        ESTUDIANTE,
        Rol.ESTUDIANTE,
        DATOS_GENERALES.replace(
            "{\"datos_generales\"",
            "{\"identificacion\":{\"codigo\":\"FALSO\"},\"datos_generales\""));
    subirFotos(ESTUDIANTE, Rol.ESTUDIANTE);
    enviar(ESTUDIANTE, Rol.ESTUDIANTE)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.datos.identificacion.codigo").value(CODIGO_PUENTE));
  }

  @Test
  void catedraticoPublicaDirectoSuPropiaInspeccion() throws Exception {
    iniciarBorrador(CATEDRATICO, Rol.CATEDRATICO);
    guardarAvance(CATEDRATICO, Rol.CATEDRATICO, DATOS_GENERALES);
    subirFotos(CATEDRATICO, Rol.CATEDRATICO);
    enviar(CATEDRATICO, Rol.CATEDRATICO)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PUBLICADA"))
        .andExpect(jsonPath("$.publicada_en").isNotEmpty())
        .andExpect(jsonPath("$.indice_condicion").value(100.0))
        .andExpect(jsonPath("$.estado_calculado").value("BUENO"));
  }

  @Test
  void enviarSinDatosGeneralesEsUn422ConElCampoRequerido() throws Exception {
    iniciarBorrador(ESTUDIANTE, Rol.ESTUDIANTE);
    guardarAvance(ESTUDIANTE, Rol.ESTUDIANTE, "{}");
    enviar(ESTUDIANTE, Rol.ESTUDIANTE)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value(Matchers.endsWith("inspeccion-no-publicable")))
        .andExpect(
            jsonPath("$.errores[?(@.campo == 'datos_generales.longitud_total')].mensaje")
                .value(Matchers.hasItem("Requerido")));
  }

  private void iniciarBorrador(UUID actor, Rol rol) throws Exception {
    mvc.perform(
            post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(alta())
                .with(csrf())
                .with(como(rol, actor)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.estado").value("BORRADOR"));
    mvc.perform(get(BASE + "/" + BORRADOR + "/datos-permanentes").with(como(rol, actor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.datos.identificacion.codigo").value(CODIGO_PUENTE));
    mvc.perform(
            post(BASE + "/" + BORRADOR + "/confirmacion-datos-permanentes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"datos\":{}}")
                .with(csrf())
                .with(como(rol, actor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.datos_permanentes_confirmados_en").isNotEmpty());
  }

  private void guardarAvance(UUID actor, Rol rol, String datos) throws Exception {
    long version =
        jdbc.sql("select version from inspeccion where id = :id")
            .param("id", BORRADOR)
            .query(Long.class)
            .single();
    mvc.perform(
            put(BASE + "/" + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"datos\":%s,\"version\":%d,\"grupos_sin_danos\":%s}"
                        .formatted(datos, version, GRUPOS_SIN_DANOS))
                .with(csrf())
                .with(como(rol, actor)))
        .andExpect(status().isOk());
  }

  private void subirFotos(UUID actor, Rol rol) throws Exception {
    for (int i = 0; i < 4; i++) {
      mvc.perform(
              multipart(BASE + "/" + BORRADOR + "/fotos")
                  .file(new MockMultipartFile("foto", "foto.jpg", MediaType.IMAGE_JPEG_VALUE, JPEG))
                  .file(
                      new MockMultipartFile(
                          "miniatura", "miniatura.jpg", MediaType.IMAGE_JPEG_VALUE, JPEG))
                  .param("id", UUID.randomUUID().toString())
                  .param("capturada_en", "2026-10-20T15:30:00Z")
                  .with(csrf())
                  .with(como(rol, actor)))
          .andExpect(status().isCreated());
    }
  }

  private ResultActions enviar(UUID actor, Rol rol) throws Exception {
    return mvc.perform(
        post(BASE + "/" + BORRADOR + "/envio")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}")
            .with(csrf())
            .with(como(rol, actor)));
  }

  private String alta() {
    return """
        {"id":"%s","puente_id":"%s","fecha_inspeccion":"2026-10-20","dispositivo_id":"tablet-sieca"}
        """
        .formatted(BORRADOR, PUENTE);
  }
}
