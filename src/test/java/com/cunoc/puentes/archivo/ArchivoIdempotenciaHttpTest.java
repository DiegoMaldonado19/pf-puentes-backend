package com.cunoc.puentes.archivo;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.Rol;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import software.amazon.awssdk.services.s3.S3Client;

/** M06 activo; variantes M05 y baja M09 pendientes tras el RED integral inicial de 14 casos. */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/inspeccion/consulta.sql")
@Sql(
    scripts = "/inspeccion/consulta-limpiar.sql",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
abstract class ArchivoIdempotenciaHttpTest extends PruebaIntegracion {
  protected static final UUID AUTOR = UUID.fromString("0199c200-0000-7000-8000-000000000011");
  protected static final UUID OTRO = UUID.fromString("0199c200-0000-7000-8000-000000000012");
  protected static final UUID PADRE = UUID.fromString("0199c200-0000-7000-8000-0000000000b4");
  private static final String PREFIJO_PADRE = "/" + PADRE + "/";
  protected static final byte[] PDF = "%PDF-1.7\noriginal".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0};
  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;

  @AfterEach
  void limpiarObjetosDelCaso() {
    if (jdbc.sql("select to_regclass('public.intento_documento') is not null")
        .query(Boolean.class)
        .single()) {
      jdbc.sql("delete from intento_documento where inspeccion_id = :id")
          .param("id", PADRE)
          .update();
    }
    try (S3Client s3 = cliente()) {
      for (String clave : objetos()) {
        s3.deleteObject(o -> o.bucket(AlmacenamientoMinio.BUCKET).key(clave));
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false})
  void claveVinculaBytesMimeUuidYMetadatosSinMutar(boolean foto) throws Exception {
    UUID id = UUID.randomUUID();
    creada(subir(foto, id, "vinculada", foto ? JPEG : PDF, AUTOR));
    List<String> antes = instantanea();
    byte[] distintos =
        foto
            ? new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1}
            : "%PDF-1.7\notro".getBytes(StandardCharsets.US_ASCII);
    problema(
        subir(foto, id, "vinculada", distintos, AUTOR), 409, "clave-idempotencia-incompatible");
    assertThat(instantanea()).isEqualTo(antes);
    problema(
        subir(foto, UUID.randomUUID(), "vinculada", foto ? JPEG : PDF, AUTOR),
        409,
        "clave-idempotencia-incompatible");
    assertThat(instantanea()).isEqualTo(antes);
    var mime = peticion(foto, id, foto ? JPEG : PDF, "application/octet-stream");
    problema(ejecutar(mime, "vinculada", AUTOR), 409, "clave-idempotencia-incompatible");
    assertThat(instantanea()).isEqualTo(antes);
    if (foto) {
      var metadatos = peticion(true, id, JPEG, "image/jpeg").param("elemento_ref", "otro-elemento");
      problema(ejecutar(metadatos, "vinculada", AUTOR), 409, "clave-idempotencia-incompatible");
      assertThat(instantanea()).isEqualTo(antes);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false})
  void replayExactoConservaUrlOriginalTrasBajaYNoResucitaArchivo(boolean foto) throws Exception {
    UUID id = UUID.randomUUID();
    MvcResult original = subir(foto, id, " original ", foto ? JPEG : PDF, AUTOR);
    creada(original);
    assertThat(borrar(id, null, AUTOR).getResponse().getStatus()).isEqualTo(204);
    List<String> antes = instantanea();
    igual(subir(foto, id, "original", foto ? JPEG : PDF, AUTOR), original);
    assertThat(instantanea()).isEqualTo(antes);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false})
  void concurrenciaDevuelveUnResultadoYUnManifiestoDeObjetos(boolean foto) throws Exception {
    UUID id = UUID.randomUUID();
    CountDownLatch inicio = new CountDownLatch(1);
    try (var hilos = Executors.newFixedThreadPool(2)) {
      var uno =
          hilos.submit(
              () -> {
                inicio.await();
                return subir(foto, id, "concurrente", foto ? JPEG : PDF, AUTOR);
              });
      var dos =
          hilos.submit(
              () -> {
                inicio.await();
                return subir(foto, id, "concurrente", foto ? JPEG : PDF, AUTOR);
              });
      inicio.countDown();
      MvcResult primera = uno.get(20, TimeUnit.SECONDS);
      MvcResult segunda = dos.get(20, TimeUnit.SECONDS);
      creada(primera);
      igual(segunda, primera);
    }
    assertThat(objetos()).hasSize(foto ? 2 : 1);
    assertThat(
            jdbc.sql("select count(*) from archivo where inspeccion_id = :id")
                .param("id", PADRE)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql("select count(*) from idempotencia where usuario_id = :id")
                .param("id", AUTOR)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false})
  void sinClaveYBlancaConservanDeduplicacionUuidY201(boolean foto) throws Exception {
    UUID id = UUID.randomUUID();
    creada(subir(foto, id, null, foto ? JPEG : PDF, AUTOR));
    List<String> antes = instantanea();
    creada(subir(foto, id, "   ", foto ? JPEG : PDF, AUTOR));
    assertThat(instantanea()).isEqualTo(antes);
    assertThat(objetos()).hasSize(foto ? 2 : 1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false})
  void autorizacionActualPrecedeReplayYNoFiltraResultado(boolean foto) throws Exception {
    UUID id = UUID.randomUUID();
    creada(subir(foto, id, "permiso", foto ? JPEG : PDF, AUTOR));
    List<String> antes = instantanea();
    problema(subir(foto, id, "permiso", foto ? JPEG : PDF, OTRO), 404, "inspeccion-no-encontrada");
    assertThat(instantanea()).isEqualTo(antes);
    jdbc.sql("update inspeccion set autor_id = :autor where id = :id")
        .param("autor", OTRO)
        .param("id", PADRE)
        .update();
    antes = instantanea();
    problema(subir(foto, id, "permiso", foto ? JPEG : PDF, AUTOR), 404, "inspeccion-no-encontrada");
    assertThat(instantanea()).isEqualTo(antes);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false})
  void formatoInvalidoNoConsumeClaveNiCambiaEfectos(boolean foto) throws Exception {
    UUID id = UUID.randomUUID();
    List<String> antes = instantanea();
    problema(subir(foto, id, "corregible", new byte[] {0}, AUTOR), 422, "archivo-invalido");
    assertThat(instantanea()).isEqualTo(antes);
    creada(subir(foto, id, "corregible", foto ? JPEG : PDF, AUTOR));
  }

  /** Especificación ejecutable futura M09; no seleccionada por el runner PDF M06. */
  void pendienteM09BajaReproduce204TrasAusenciaSinTocarActividadNiObjetos() throws Exception {
    UUID id = UUID.randomUUID();
    creada(subir(false, id, null, PDF, AUTOR));
    MvcResult original = borrar(id, "baja", AUTOR);
    assertThat(original.getResponse().getStatus()).isEqualTo(204);
    assertThat(original.getResponse().getContentAsByteArray()).isEmpty();
    assertThat(objetos()).isEmpty();
    List<String> antes = instantanea();
    igual(borrar(id, "baja", AUTOR), original);
    assertThat(instantanea()).isEqualTo(antes);
    problema(borrar(UUID.randomUUID(), "baja", AUTOR), 404, "archivo-no-encontrado");
    assertThat(instantanea()).isEqualTo(antes);
  }

  /** Especificación ejecutable futura M09; conserva íntegramente las aserciones del RED inicial. */
  void pendienteM09BajaRetenidaRevalidaAutorActualDelPadre() throws Exception {
    UUID id = UUID.randomUUID();
    creada(subir(false, id, null, PDF, AUTOR));
    assertThat(borrar(id, "baja-permiso", AUTOR).getResponse().getStatus()).isEqualTo(204);
    jdbc.sql("update inspeccion set autor_id = :autor where id = :id")
        .param("autor", OTRO)
        .param("id", PADRE)
        .update();
    List<String> antes = instantanea();
    problema(borrar(id, "baja-permiso", AUTOR), 404, "inspeccion-no-encontrada");
    assertThat(instantanea()).isEqualTo(antes);
  }

  protected MvcResult subir(boolean foto, UUID id, String clave, byte[] bytes, UUID autor)
      throws Exception {
    return ejecutar(
        peticion(foto, id, bytes, foto ? "image/jpeg" : "application/pdf"), clave, autor);
  }

  protected MockMultipartHttpServletRequestBuilder peticion(
      boolean foto, UUID id, byte[] bytes, String mime) {
    var peticion =
        multipart("/api/v1/inspecciones/" + PADRE + (foto ? "/fotos" : "/documentos"))
            .file(new MockMultipartFile(foto ? "foto" : "pdf", "cliente", mime, bytes));
    peticion.param("id", id.toString());
    if (foto) {
      peticion.file(new MockMultipartFile("miniatura", "miniatura", "image/jpeg", JPEG));
      peticion.param("capturada_en", "2026-10-04T15:30:00Z");
    }
    return peticion;
  }

  protected MvcResult ejecutar(
      MockMultipartHttpServletRequestBuilder peticion, String clave, UUID autor) throws Exception {
    if (clave != null) {
      peticion.header("Idempotency-Key", clave);
    }
    return mvc.perform(peticion.with(como(Rol.ESTUDIANTE, autor))).andReturn();
  }

  protected MvcResult borrar(UUID id, String clave, UUID autor) throws Exception {
    var peticion = delete("/api/v1/archivos/" + id).with(como(Rol.ESTUDIANTE, autor));
    if (clave != null) {
      peticion.header("Idempotency-Key", clave);
    }
    return mvc.perform(peticion).andReturn();
  }

  protected static void creada(MvcResult resultado) {
    assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
    assertThat(resultado.getResponse().getContentType()).startsWith("application/json");
  }

  protected static void igual(MvcResult actual, MvcResult original) {
    assertThat(actual.getResponse().getStatus()).isEqualTo(original.getResponse().getStatus());
    assertThat(actual.getResponse().getContentType())
        .isEqualTo(original.getResponse().getContentType());
    assertThat(actual.getResponse().getContentAsByteArray())
        .isEqualTo(original.getResponse().getContentAsByteArray());
  }

  protected static void problema(MvcResult resultado, int estado, String tipo) throws Exception {
    assertThat(resultado.getResponse().getStatus()).isEqualTo(estado);
    assertThat(resultado.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(resultado.getResponse().getContentAsString()).contains(tipo);
  }

  protected List<String> instantanea() {
    var filas =
        new java.util.ArrayList<>(
            jdbc.sql(
                    "select row_to_json(a)::text from archivo a where inspeccion_id = :id order by id")
                .param("id", PADRE)
                .query(String.class)
                .list());
    filas.addAll(
        jdbc.sql("select row_to_json(i)::text from inspeccion i where id = :id")
            .param("id", PADRE)
            .query(String.class)
            .list());
    filas.addAll(
        jdbc.sql(
                "select row_to_json(i)::text from idempotencia i where usuario_id in (:autor, :otro) order by id")
            .param("autor", AUTOR)
            .param("otro", OTRO)
            .query(String.class)
            .list());
    if (jdbc.sql("select to_regclass('public.intento_documento') is not null")
        .query(Boolean.class)
        .single()) {
      filas.addAll(
          jdbc.sql(
                  "select row_to_json(i)::text from intento_documento i where inspeccion_id = :id order by usuario_id, clave")
              .param("id", PADRE)
              .query(String.class)
              .list());
    }
    filas.addAll(objetos());
    return filas;
  }

  protected List<String> objetos() {
    try (S3Client s3 = cliente()) {
      return s3
          .listObjectsV2Paginator(o -> o.bucket(AlmacenamientoMinio.BUCKET))
          .contents()
          .stream()
          .map(o -> o.key())
          .filter(clave -> clave.contains(PREFIJO_PADRE))
          .sorted()
          .toList();
    }
  }

  protected static S3Client cliente() {
    return AlmacenamientoMinio.cliente(
        URI.create(MINIO.getS3URL()), MINIO.getUserName(), MINIO.getPassword());
  }
}
