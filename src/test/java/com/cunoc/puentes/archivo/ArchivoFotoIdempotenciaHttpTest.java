package com.cunoc.puentes.archivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Contrato M05 por HTTP real; M09 permanece sin seleccionar en la base. */
class ArchivoFotoIdempotenciaHttpTest extends ArchivoIdempotenciaHttpTest {
  private static final byte[] FOTO = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0};
  @MockitoSpyBean AlmacenamientoMinio almacenamiento;

  @AfterEach
  void restablecerAlmacenamiento() {
    reset(almacenamiento);
  }

  @Override
  @ParameterizedTest
  @ValueSource(booleans = {true})
  void claveVinculaBytesMimeUuidYMetadatosSinMutar(boolean foto) throws Exception {
    super.claveVinculaBytesMimeUuidYMetadatosSinMutar(foto);
  }

  @Override
  @ParameterizedTest
  @ValueSource(booleans = {true})
  void replayExactoConservaUrlOriginalTrasBajaYNoResucitaArchivo(boolean foto) throws Exception {
    super.replayExactoConservaUrlOriginalTrasBajaYNoResucitaArchivo(foto);
  }

  @Override
  @ParameterizedTest
  @ValueSource(booleans = {true})
  void concurrenciaDevuelveUnResultadoYUnManifiestoDeObjetos(boolean foto) throws Exception {
    super.concurrenciaDevuelveUnResultadoYUnManifiestoDeObjetos(foto);
  }

  @Override
  @ParameterizedTest
  @ValueSource(booleans = {true})
  void sinClaveYBlancaConservanDeduplicacionUuidY201(boolean foto) throws Exception {
    super.sinClaveYBlancaConservanDeduplicacionUuidY201(foto);
  }

  @Override
  @ParameterizedTest
  @ValueSource(booleans = {true})
  void autorizacionActualPrecedeReplayYNoFiltraResultado(boolean foto) throws Exception {
    super.autorizacionActualPrecedeReplayYNoFiltraResultado(foto);
  }

  @Override
  @ParameterizedTest
  @ValueSource(booleans = {true})
  void formatoInvalidoNoConsumeClaveNiCambiaEfectos(boolean foto) throws Exception {
    super.formatoInvalidoNoConsumeClaveNiCambiaEfectos(foto);
  }

  @Test
  void miniaturaYTodosLosMetadatosParticipanEnHuella() throws Exception {
    UUID id = UUID.randomUUID();
    var original = subir(true, id, "metadatos", FOTO, AUTOR);
    creada(original);
    List<String> antes = estado();
    for (String campo : List.of("elemento_ref", "latitud", "longitud", "capturada_en")) {
      var peticion = peticion(true, id, FOTO, "image/jpeg");
      String valor =
          switch (campo) {
            case "elemento_ref" -> "otro-elemento";
            case "capturada_en" -> "2026-10-04T15:30:01Z";
            default -> "1.5";
          };
      // Reemplazar, no añadir otro valor del mismo parámetro multipart.
      peticion.with(
          request -> {
            request.setParameter(campo, valor);
            return request;
          });
      problema(ejecutar(peticion, "metadatos", AUTOR), 409, "clave-idempotencia-incompatible");
      assertThat(estado()).isEqualTo(antes);
    }
    for (boolean cambiarMime : List.of(false, true)) {
      var peticion =
          org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                  "/api/v1/inspecciones/" + PADRE + "/fotos")
              .file(
                  new org.springframework.mock.web.MockMultipartFile(
                      "foto", "cliente", "image/jpeg", FOTO))
              .file(
                  new org.springframework.mock.web.MockMultipartFile(
                      "miniatura",
                      "otra.jpg",
                      cambiarMime ? "application/octet-stream" : "image/jpeg",
                      cambiarMime ? FOTO : new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1}))
              .param("id", id.toString())
              .param("capturada_en", "2026-10-04T15:30:00Z");
      problema(ejecutar(peticion, "metadatos", AUTOR), 409, "clave-idempotencia-incompatible");
      assertThat(estado()).isEqualTo(antes);
    }
    igual(subir(true, id, "metadatos", FOTO, AUTOR), original);
    assertThat(estado()).isEqualTo(antes);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void segundoPutParcialOInciertoRecuperaDosIdentidadesSinReescribir(boolean incierto)
      throws Exception {
    UUID id = UUID.randomUUID();
    AtomicInteger llamadas = new AtomicInteger();
    doAnswer(
            llamada -> {
              int numero = llamadas.incrementAndGet();
              if (numero == 2 && !incierto) {
                throw new IllegalStateException("Segundo PUT no iniciado");
              }
              llamada.callRealMethod();
              if (numero == 2) {
                throw new IllegalStateException("Respuesta perdida después del segundo PUT real");
              }
              return null;
            })
        .when(almacenamiento)
        .guardar(anyString(), any(byte[].class), anyString());
    fallo(() -> subir(true, id, "parcial", FOTO, AUTOR));
    assertThat(llamadas.get()).isEqualTo(2);
    List<String> claves = objetos();
    assertThat(claves).hasSize(incierto ? 2 : 1);
    sinResultado(id);
    integridad();
    reset(almacenamiento);
    // El objeto ya escrito nunca debe volver a guardarse.
    doAnswer(
            llamada -> {
              assertThat(claves).doesNotContain((String) llamada.getArgument(0));
              return llamada.callRealMethod();
            })
        .when(almacenamiento)
        .guardar(anyString(), any(byte[].class), anyString());
    var original = subir(true, id, "parcial", FOTO, AUTOR);
    creada(original);
    assertThat(objetos()).hasSize(2).containsAll(claves);
    integridad();
    List<String> antes = estado();
    igual(subir(true, id, "parcial", FOTO, AUTOR), original);
    assertThat(estado()).isEqualTo(antes);
  }

  @Test
  void rollbackFinalConservaDosObjetosYActividadYRecuperaRespuestaExacta() throws Exception {
    UUID id = UUID.randomUUID();
    String actividad =
        jdbc.sql("select row_to_json(i)::text from inspeccion i where id = :id")
            .param("id", PADRE)
            .query(String.class)
            .single();
    jdbc.sql(
            "alter table idempotencia add constraint prueba_foto_resultado check (clave <> 'rollback-foto')")
        .update();
    try {
      fallo(() -> subir(true, id, "rollback-foto", FOTO, AUTOR));
    } finally {
      jdbc.sql("alter table idempotencia drop constraint prueba_foto_resultado").update();
    }
    sinResultado(id);
    assertThat(
            jdbc.sql("select row_to_json(i)::text from inspeccion i where id = :id")
                .param("id", PADRE)
                .query(String.class)
                .single())
        .isEqualTo(actividad);
    List<String> claves = objetos();
    assertThat(claves).hasSize(2);
    integridad();
    var original = subir(true, id, "rollback-foto", FOTO, AUTOR);
    creada(original);
    assertThat(objetos()).isEqualTo(claves);
    integridad();
    List<String> antes = estado();
    igual(subir(true, id, "rollback-foto", FOTO, AUTOR), original);
    assertThat(estado()).isEqualTo(antes);
  }

  @Test
  void reservaPdfPendienteRechazaFotoKeyedSinCambiarEfectos() throws Exception {
    UUID id = UUID.randomUUID();
    doAnswer(
            llamada -> {
              throw new IllegalStateException("PUT no iniciado");
            })
        .when(almacenamiento)
        .guardar(anyString(), any(byte[].class), anyString());
    fallo(() -> subir(false, id, "pdf-pendiente", PDF, AUTOR));
    reset(almacenamiento);
    List<String> antes = estado();
    problema(subir(true, id, "foto-distinta", FOTO, AUTOR), 409, "operacion-en-proceso");
    assertThat(estado()).isEqualTo(antes);
    problema(subir(true, id, "pdf-pendiente", FOTO, AUTOR), 409, "clave-idempotencia-incompatible");
    assertThat(estado()).isEqualTo(antes);
    creada(subir(false, id, "pdf-pendiente", PDF, AUTOR));
  }

  @Test
  void replayNoExigeBorradorYMutacionNuevaNoConsumeClave() throws Exception {
    UUID id = UUID.randomUUID();
    var original = subir(true, id, "tardia-foto", FOTO, AUTOR);
    creada(original);
    jdbc.sql("update inspeccion set estado = 'ENVIADA' where id = :id").param("id", PADRE).update();
    List<String> antes = estado();
    igual(subir(true, id, "tardia-foto", FOTO, AUTOR), original);
    assertThat(estado()).isEqualTo(antes);
    problema(
        subir(true, UUID.randomUUID(), "nueva-foto", FOTO, AUTOR), 409, "inspeccion-no-editable");
    assertThat(estado()).isEqualTo(antes);
  }

  @Test
  void uuidPdfConfirmadoConservaDeduplicacionYResultadoFotoKeyed() throws Exception {
    UUID id = UUID.randomUUID();
    creada(subir(false, id, null, PDF, AUTOR));
    List<String> claves = objetos();
    var original = subir(true, id, "😀".repeat(100), FOTO, AUTOR);
    creada(original);
    assertThat(original.getResponse().getContentAsString()).contains("\"tipo\":\"PDF\"");
    assertThat(objetos()).isEqualTo(claves);
    List<String> antes = estado();
    igual(subir(true, id, "😀".repeat(100), FOTO, AUTOR), original);
    assertThat(estado()).isEqualTo(antes);
  }

  @Test
  void rolYPadreEliminadoImpidenRevelarReplay() throws Exception {
    UUID id = UUID.randomUUID();
    creada(subir(true, id, "protegida", FOTO, AUTOR));
    List<String> antes = estado();
    var prohibida =
        mvc.perform(
                peticion(true, id, FOTO, "image/jpeg")
                    .header("Idempotency-Key", "protegida")
                    .with(
                        com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como(
                            com.cunoc.puentes.usuario.Rol.ADMINISTRADOR, AUTOR)))
            .andReturn();
    assertThat(prohibida.getResponse().getStatus()).isEqualTo(403);
    assertThat(estado()).isEqualTo(antes);
    jdbc.sql("update inspeccion set eliminado_en = now() where id = :id")
        .param("id", PADRE)
        .update();
    antes = estado();
    problema(subir(true, id, "protegida", FOTO, AUTOR), 404, "inspeccion-no-encontrada");
    assertThat(estado()).isEqualTo(antes);
  }

  @Test
  void normalizaUuidInstanteYNumerosExcluyeNombreYParametrosAjenos() throws Exception {
    UUID id = UUID.randomUUID();
    var primera = peticion(true, id, FOTO, "image/jpeg").param("latitud", "1.50");
    var original = ejecutar(primera, "canonico-foto", AUTOR);
    creada(original);
    List<String> antes = estado();
    var otra =
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                "/api/v1/inspecciones/"
                    + PADRE.toString().toUpperCase(java.util.Locale.ROOT)
                    + "/fotos")
            .file(
                new org.springframework.mock.web.MockMultipartFile(
                    "foto", "renombrada.jpg", "image/jpeg", FOTO))
            .file(
                new org.springframework.mock.web.MockMultipartFile(
                    "miniatura", "renombrada-mini.jpg", "image/jpeg", FOTO))
            .param("id", id.toString().toUpperCase(java.util.Locale.ROOT))
            .param("latitud", "1.5")
            .param("capturada_en", "2026-10-04T09:30:00-06:00")
            .param("ajeno", "no participa")
            .header("X-Sgp-Sync", "true");
    igual(ejecutar(otra, " canonico-foto ", AUTOR), original);
    assertThat(estado()).isEqualTo(antes);
    problema(
        subir(true, UUID.randomUUID(), "😀".repeat(101), FOTO, AUTOR),
        400,
        "idempotencia-invalida");
    assertThat(estado()).isEqualTo(antes);
  }

  @Test
  void reservaFotoImpideOtrasClavesYFlujoHistoricoSinObjetosAdicionales() throws Exception {
    UUID id = UUID.randomUUID();
    doAnswer(
            llamada -> {
              throw new IllegalStateException("PUT no iniciado");
            })
        .when(almacenamiento)
        .guardar(anyString(), any(byte[].class), anyString());
    fallo(() -> subir(true, id, "reserva-foto", FOTO, AUTOR));
    reset(almacenamiento);
    List<String> antes = estado();
    for (String clave : java.util.Arrays.asList(null, "   ", "otra-foto")) {
      problema(subir(true, id, clave, FOTO, AUTOR), 409, "operacion-en-proceso");
      assertThat(estado()).isEqualTo(antes);
    }
    problema(subir(false, id, "otra-pdf", PDF, AUTOR), 409, "operacion-en-proceso");
    assertThat(estado()).isEqualTo(antes);
    creada(subir(true, id, "reserva-foto", FOTO, AUTOR));
    assertThat(objetos()).hasSize(2);
    integridad();
  }

  @Test
  void miniaturaRemotaIncompatibleNoSeSobrescribeYErrorDeLecturaNoEsAusencia() throws Exception {
    UUID id = UUID.randomUUID();
    AtomicInteger llamadas = new AtomicInteger();
    doAnswer(
            llamada -> {
              if (llamadas.incrementAndGet() == 2) {
                throw new IllegalStateException("Miniatura pendiente");
              }
              return llamada.callRealMethod();
            })
        .when(almacenamiento)
        .guardar(anyString(), any(byte[].class), anyString());
    fallo(() -> subir(true, id, "mini-remota", FOTO, AUTOR));
    reset(almacenamiento);
    String miniatura =
        jdbc.sql("select clave_miniatura from intento_documento where archivo_id = :id")
            .param("id", id)
            .query(String.class)
            .single();
    try (var s3 = cliente()) {
      s3.putObject(
          o -> o.bucket(AlmacenamientoMinio.BUCKET).key(miniatura).contentType("image/jpeg"),
          software.amazon.awssdk.core.sync.RequestBody.fromBytes(new byte[] {1, 2, 3}));
    }
    List<String> antes = estado();
    problema(subir(true, id, "mini-remota", FOTO, AUTOR), 409, "objeto-archivo-incompatible");
    assertThat(estado()).isEqualTo(antes);
    sinResultado(id);
    doAnswer(
            llamada -> {
              throw new IllegalStateException("Lectura remota incierta");
            })
        .when(almacenamiento)
        .inspeccionar(anyString());
    fallo(() -> subir(true, id, "mini-remota", FOTO, AUTOR));
    assertThat(estado()).isEqualTo(antes);
    org.mockito.Mockito.verify(almacenamiento, org.mockito.Mockito.never())
        .guardar(anyString(), any(byte[].class), anyString());
  }

  @org.springframework.beans.factory.annotation.Autowired
  org.springframework.transaction.PlatformTransactionManager gestor;

  @Test
  void replayReautorizaTrasEsperarClave() throws Exception {
    UUID id = UUID.randomUUID();
    creada(subir(true, id, "espera-foto", FOTO, AUTOR));
    byte[] clave = "espera-foto".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    long bloqueo =
        java.nio.ByteBuffer.wrap(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(
                        java.nio.ByteBuffer.allocate(20 + clave.length)
                            .putLong(AUTOR.getMostSignificantBits())
                            .putLong(AUTOR.getLeastSignificantBits())
                            .putInt(clave.length)
                            .put(clave)
                            .array()))
            .getLong();
    var adquirida = new java.util.concurrent.CountDownLatch(1);
    var liberar = new java.util.concurrent.CountDownLatch(1);
    try (var hilos = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var retenida =
          hilos.submit(
              () ->
                  new org.springframework.transaction.support.TransactionTemplate(gestor)
                      .execute(
                          tx -> {
                            jdbc.sql("select pg_advisory_xact_lock(:clave)")
                                .param("clave", bloqueo)
                                .query(Object.class)
                                .single();
                            adquirida.countDown();
                            try {
                              if (!liberar.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                                throw new AssertionError("No se liberó la clave");
                              }
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                              throw new IllegalStateException(e);
                            }
                            return true;
                          }));
      assertThat(adquirida.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      var hiloPeticion = new java.util.concurrent.atomic.AtomicReference<Thread>();
      var solicitud =
          hilos.submit(
              () -> {
                hiloPeticion.set(Thread.currentThread());
                return subir(true, id, "espera-foto", FOTO, AUTOR);
              });
      try {
        boolean esperando = false;
        long limite = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < limite) {
          Thread hilo = hiloPeticion.get();
          esperando =
              hilo != null
                  && hilo.getState() == Thread.State.TIMED_WAITING
                  && java.util.Arrays.stream(hilo.getStackTrace())
                      .anyMatch(
                          marco ->
                              marco
                                  .getClassName()
                                  .equals("com.cunoc.puentes.sync.IdempotenciaService"));
          if (esperando) break;
          Thread.sleep(10);
        }
        assertThat(esperando).isTrue();
        jdbc.sql("update inspeccion set autor_id = :autor where id = :id")
            .param("autor", OTRO)
            .param("id", PADRE)
            .update();
      } finally {
        liberar.countDown();
      }
      retenida.get(10, java.util.concurrent.TimeUnit.SECONDS);
      List<String> antes = estado();
      problema(
          solicitud.get(10, java.util.concurrent.TimeUnit.SECONDS),
          404,
          "inspeccion-no-encontrada");
      assertThat(estado()).isEqualTo(antes);
    }
  }

  private void sinResultado(UUID id) {
    assertThat(
            jdbc.sql("select count(*) from archivo where id = :id")
                .param("id", id)
                .query(Long.class)
                .single())
        .isZero();
    assertThat(
            jdbc.sql("select count(*) from idempotencia where usuario_id = :id")
                .param("id", AUTOR)
                .query(Long.class)
                .single())
        .isZero();
  }

  private void integridad() {
    try (var s3 = cliente()) {
      for (String clave : objetos()) {
        var objeto = s3.getObjectAsBytes(o -> o.bucket(AlmacenamientoMinio.BUCKET).key(clave));
        assertThat(objeto.asByteArray()).isEqualTo(FOTO);
        assertThat(objeto.response().contentType()).isEqualTo("image/jpeg");
      }
    }
  }

  private List<String> estado() {
    var filas = new java.util.ArrayList<>(instantanea());
    filas.addAll(
        jdbc.sql("select row_to_json(a)::text from auditoria a order by row_to_json(a)::text")
            .query(String.class)
            .list());
    try (var s3 = cliente()) {
      for (String clave : objetos()) {
        var objeto = s3.getObjectAsBytes(o -> o.bucket(AlmacenamientoMinio.BUCKET).key(clave));
        filas.add(
            clave
                + ":"
                + objeto.response().contentType()
                + ":"
                + java.util.Base64.getEncoder().encodeToString(objeto.asByteArray()));
      }
    }
    return filas;
  }

  private static void fallo(Peticion peticion) throws Exception {
    try {
      assertThat(peticion.ejecutar().getResponse().getStatus()).isEqualTo(500);
    } catch (jakarta.servlet.ServletException e) {
      assertThat(e.getCause()).isInstanceOf(RuntimeException.class);
    }
  }

  @FunctionalInterface
  private interface Peticion {
    org.springframework.test.web.servlet.MvcResult ejecutar() throws Exception;
  }
}
