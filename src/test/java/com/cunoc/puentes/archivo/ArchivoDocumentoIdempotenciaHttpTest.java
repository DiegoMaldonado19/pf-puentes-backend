package com.cunoc.puentes.archivo;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.cunoc.puentes.usuario.Rol;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ArchivoDocumentoIdempotenciaHttpTest extends ArchivoIdempotenciaHttpTest {
  @MockitoSpyBean AlmacenamientoMinio almacenamiento;
  @Autowired PlatformTransactionManager gestor;

  @AfterEach
  void restablecerAlmacenamiento() {
    reset(almacenamiento);
  }

  @Test
  void unicodeLegadoYAlcanceNoConsumenNiCompartenResultado() throws Exception {
    UUID id = UUID.randomUUID();
    List<String> antes = instantanea();
    problema(subir(false, id, "😀".repeat(101), PDF, AUTOR), 400, "idempotencia-invalida");
    assertThat(instantanea()).isEqualTo(antes);
    var original = subir(false, id, "😀".repeat(100), PDF, AUTOR);
    creada(original);
    antes = instantanea();
    igual(subir(false, id, "😀".repeat(100), PDF, AUTOR), original);
    assertThat(instantanea()).isEqualTo(antes);
    jdbc.sql(
            "insert into idempotencia (id, usuario_id, clave, estado_http, respuesta) values (:id, :usuario, 'legado-pdf', 201, '{}'::jsonb)")
        .param("id", UUID.randomUUID())
        .param("usuario", AUTOR)
        .update();
    antes = instantanea();
    problema(
        subir(false, UUID.randomUUID(), "legado-pdf", PDF, AUTOR),
        409,
        "clave-idempotencia-incompatible");
    assertThat(instantanea()).isEqualTo(antes);
    problema(
        ejecutar(peticion(false, id, PDF, "application/pdf"), "😀".repeat(100), OTRO),
        404,
        "inspeccion-no-encontrada");
    assertThat(instantanea()).isEqualTo(antes);
    var hermana =
        mvc.perform(
                post("/api/v1/inspecciones/" + PADRE + "/envio")
                    .header("Idempotency-Key", "😀".repeat(100))
                    .contentType("application/json")
                    .content("{}")
                    .with(como(Rol.ESTUDIANTE, AUTOR)))
            .andReturn();
    problema(hermana, 409, "clave-idempotencia-incompatible");
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void replayNoExigeEditabilidadPeroRechazaPadreEliminadoYRolProhibido() throws Exception {
    UUID id = UUID.randomUUID();
    var original = subir(false, id, "tardia", PDF, AUTOR);
    creada(original);
    jdbc.sql("update inspeccion set estado = 'ENVIADA' where id = :id").param("id", PADRE).update();
    List<String> antes = instantanea();
    igual(subir(false, id, "tardia", PDF, AUTOR), original);
    assertThat(instantanea()).isEqualTo(antes);
    problema(subir(false, UUID.randomUUID(), "nueva", PDF, AUTOR), 409, "inspeccion-no-editable");
    assertThat(instantanea()).isEqualTo(antes);
    var prohibida =
        mvc.perform(
                peticion(false, id, PDF, "application/pdf")
                    .header("Idempotency-Key", "tardia")
                    .with(como(Rol.ADMINISTRADOR, AUTOR)))
            .andReturn();
    assertThat(prohibida.getResponse().getStatus()).isEqualTo(403);
    assertThat(instantanea()).isEqualTo(antes);
    jdbc.sql("update inspeccion set eliminado_en = now() where id = :id")
        .param("id", PADRE)
        .update();
    antes = instantanea();
    problema(subir(false, id, "tardia", PDF, AUTOR), 404, "inspeccion-no-encontrada");
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void nombreClienteYUuidCanonicoNoCambianHuella() throws Exception {
    UUID id = UUID.randomUUID();
    var primera = subir(false, id, "canonico", PDF, AUTOR);
    creada(primera);
    var peticion =
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                "/api/v1/inspecciones/"
                    + PADRE.toString().toUpperCase(java.util.Locale.ROOT)
                    + "/documentos")
            .file(
                new org.springframework.mock.web.MockMultipartFile(
                    "pdf", "otro-nombre.pdf", "application/pdf", PDF))
            .param("id", id.toString().toUpperCase(java.util.Locale.ROOT));
    List<String> antes = instantanea();
    igual(ejecutar(peticion, "canonico", AUTOR), primera);
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void clavesDiferentesYFlujoSinClaveCompartenUnSoloUuid() throws Exception {
    UUID id = UUID.randomUUID();
    CountDownLatch inicio = new CountDownLatch(1);
    try (var hilos = Executors.newFixedThreadPool(3)) {
      var una =
          hilos.submit(
              () -> {
                inicio.await();
                return subir(false, id, "uno", PDF, AUTOR);
              });
      var otra =
          hilos.submit(
              () -> {
                inicio.await();
                return subir(false, id, "dos", PDF, AUTOR);
              });
      var historica =
          hilos.submit(
              () -> {
                inicio.await();
                return subir(false, id, null, PDF, AUTOR);
              });
      inicio.countDown();
      creada(una.get(20, TimeUnit.SECONDS));
      creada(otra.get(20, TimeUnit.SECONDS));
      creada(historica.get(20, TimeUnit.SECONDS));
    }
    assertThat(objetos()).hasSize(1);
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
        .isEqualTo(2);
    integridad(objetos().getFirst());
  }

  @Test
  void rollbackFinalRealConservaIntencionYReutilizaObjetoIntegro() throws Exception {
    UUID id = UUID.randomUUID();
    String actividad = actividad();
    jdbc.sql(
            "alter table idempotencia add constraint prueba_pdf_resultado check (clave <> 'rollback-pdf')")
        .update();
    try {
      falloTransitorio(() -> subir(false, id, "rollback-pdf", PDF, AUTOR));
    } finally {
      jdbc.sql("alter table idempotencia drop constraint prueba_pdf_resultado").update();
    }
    assertThat(actividad()).isEqualTo(actividad);
    assertThat(
            jdbc.sql("select count(*) from archivo where id = :id")
                .param("id", id)
                .query(Long.class)
                .single())
        .isZero();
    assertThat(objetos()).hasSize(1);
    String clave = objetos().getFirst();
    integridad(clave);
    assertThat(
            jdbc.sql("select to_regclass('public.intento_documento') is not null")
                .query(Boolean.class)
                .single())
        .isTrue();
    assertThat(
            jdbc.sql(
                    "select count(*) from intento_documento where archivo_id = :id and not finalizada")
                .param("id", id)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    creada(subir(false, id, "rollback-pdf", PDF, AUTOR));
    assertThat(objetos()).containsExactly(clave);
    integridad(clave);
    assertThat(
            jdbc.sql("select count(*) from idempotencia where clave = 'rollback-pdf'")
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void putInciertoSeInspeccionaSinRepetirEscrituraNiCambiarClave() throws Exception {
    UUID id = UUID.randomUUID();
    AtomicReference<String> escrita = new AtomicReference<>();
    doAnswer(
            llamada -> {
              llamada.callRealMethod();
              escrita.set(llamada.getArgument(0));
              throw new IllegalStateException("Fallo de transporte después de PUT real");
            })
        .when(almacenamiento)
        .guardar(anyString(), any(byte[].class), anyString());
    falloTransitorio(() -> subir(false, id, "incierta", PDF, AUTOR));
    assertThat(escrita.get()).isNotNull();
    integridad(escrita.get());
    // Un segundo PUT seguiría fallando: solo una inspección íntegra permite finalizar.
    creada(subir(false, id, "incierta", PDF, AUTOR));
    assertThat(objetos()).containsExactly(escrita.get());
    integridad(escrita.get());
  }

  @Test
  void claveConIntencionPendienteNoPuedeReutilizarseEnEnvio() throws Exception {
    UUID id = UUID.randomUUID();
    doAnswer(
            llamada -> {
              throw new IllegalStateException("PUT no salió del cliente");
            })
        .when(almacenamiento)
        .guardar(anyString(), any(byte[].class), anyString());
    falloTransitorio(() -> subir(false, id, "pendiente-otra-ruta", PDF, AUTOR));
    reset(almacenamiento);
    List<String> antes = instantanea();
    problema(
        mvc.perform(
                post("/api/v1/inspecciones/" + PADRE + "/envio")
                    .header("Idempotency-Key", "pendiente-otra-ruta")
                    .contentType("application/json")
                    .content("{}")
                    .with(como(Rol.ESTUDIANTE, AUTOR)))
            .andReturn(),
        409,
        "clave-idempotencia-incompatible");
    assertThat(instantanea()).isEqualTo(antes);
    creada(subir(false, id, "pendiente-otra-ruta", PDF, AUTOR));
  }

  @Test
  void objetoIncompatibleNoSeSobrescribeNiSeAdmiteResultado() throws Exception {
    UUID id = UUID.randomUUID();
    doAnswer(
            llamada -> {
              throw new IllegalStateException("PUT no salió del cliente");
            })
        .when(almacenamiento)
        .guardar(anyString(), any(byte[].class), anyString());
    falloTransitorio(() -> subir(false, id, "faltante", PDF, AUTOR));
    assertThat(
            jdbc.sql("select to_regclass('public.intento_documento') is not null")
                .query(Boolean.class)
                .single())
        .isTrue();
    String clave =
        jdbc.sql("select clave_objeto from intento_documento where archivo_id = :id")
            .param("id", id)
            .query(String.class)
            .single();
    reset(almacenamiento);
    try (var s3 = cliente()) {
      s3.putObject(
          o -> o.bucket(AlmacenamientoMinio.BUCKET).key(clave).contentType("application/pdf"),
          software.amazon.awssdk.core.sync.RequestBody.fromBytes(new byte[] {1, 2, 3}));
    }
    List<String> antes = instantanea();
    problema(subir(false, id, "faltante", PDF, AUTOR), 409, "objeto-archivo-incompatible");
    assertThat(instantanea()).isEqualTo(antes);
    try (var s3 = cliente()) {
      assertThat(
              s3.getObjectAsBytes(o -> o.bucket(AlmacenamientoMinio.BUCKET).key(clave))
                  .asByteArray())
          .containsExactly((byte) 1, (byte) 2, (byte) 3);
      s3.deleteObject(o -> o.bucket(AlmacenamientoMinio.BUCKET).key(clave));
    }
    creada(subir(false, id, "faltante", PDF, AUTOR));
    assertThat(objetos()).containsExactly(clave);
    integridad(clave);
  }

  @Test
  void esperaAgotadaYReautorizacionAntesDeReplay() throws Exception {
    UUID id = UUID.randomUUID();
    creada(subir(false, id, "espera", PDF, AUTOR));
    CountDownLatch bloqueado = new CountDownLatch(1);
    CountDownLatch liberar = new CountDownLatch(1);
    try (var hilos = Executors.newFixedThreadPool(2)) {
      var bloqueo =
          hilos.submit(
              () ->
                  new TransactionTemplate(gestor)
                      .execute(
                          estado -> {
                            jdbc.sql("select pg_advisory_xact_lock(:clave)")
                                .param("clave", bloqueoClave("espera"))
                                .query(Object.class)
                                .single();
                            bloqueado.countDown();
                            esperar(liberar);
                            return true;
                          }));
      assertThat(bloqueado.await(5, TimeUnit.SECONDS)).isTrue();
      List<String> antes = instantanea();
      try {
        problema(subir(false, id, "espera", PDF, AUTOR), 409, "operacion-en-proceso");
        assertThat(instantanea()).isEqualTo(antes);
      } finally {
        liberar.countDown();
      }
      bloqueo.get(10, TimeUnit.SECONDS);
    }
    // Espera real sobre la fila: la comprobación previa ve al autor anterior;
    // el callback debe leer el propietario confirmado después de adquirir el bloqueo.
    CountDownLatch fila = new CountDownLatch(1);
    CountDownLatch cambio = new CountDownLatch(1);
    try (var hilos = Executors.newFixedThreadPool(2)) {
      var bloqueo =
          hilos.submit(
              () ->
                  new TransactionTemplate(gestor)
                      .execute(
                          estado -> {
                            jdbc.sql("update inspeccion set autor_id = :otro where id = :id")
                                .param("otro", OTRO)
                                .param("id", PADRE)
                                .update();
                            fila.countDown();
                            esperar(cambio);
                            return true;
                          }));
      assertThat(fila.await(5, TimeUnit.SECONDS)).isTrue();
      var peticion = hilos.submit(() -> subir(false, UUID.randomUUID(), "nuevo-autor", PDF, AUTOR));
      try {
        esperarBloqueoSql();
      } finally {
        cambio.countDown();
      }
      bloqueo.get(10, TimeUnit.SECONDS);
      problema(peticion.get(10, TimeUnit.SECONDS), 404, "inspeccion-no-encontrada");
      List<String> antes = instantanea();
      problema(subir(false, id, "espera", PDF, AUTOR), 404, "inspeccion-no-encontrada");
      assertThat(instantanea()).isEqualTo(antes);
    }
  }

  @Test
  void conservaLimitePdfExactoYCupoSinConsumirClavesRechazadas() throws Exception {
    byte[] limite = new byte[20 * 1024 * 1024];
    System.arraycopy(PDF, 0, limite, 0, PDF.length);
    byte[] excedido = java.util.Arrays.copyOf(limite, limite.length + 1);
    List<String> antes = instantanea();
    problema(subir(false, UUID.randomUUID(), "exceso", excedido, AUTOR), 422, "archivo-invalido");
    assertThat(instantanea()).isEqualTo(antes);
    creada(subir(false, UUID.randomUUID(), "limite", limite, AUTOR));
    for (int i = 1; i < 10; i++) {
      creada(subir(false, UUID.randomUUID(), "cupo-" + i, PDF, AUTOR));
    }
    antes = instantanea();
    problema(
        subir(false, UUID.randomUUID(), "cupo-rechazado", PDF, AUTOR), 422, "limite-de-archivos");
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void fotoHistoricaYBajaSinClaveConservanContrato() throws Exception {
    UUID id = UUID.randomUUID();
    byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0};
    creada(subir(true, id, null, jpeg, AUTOR));
    assertThat(objetos()).hasSize(2);
    List<String> antes = instantanea();
    creada(subir(true, id, "   ", jpeg, AUTOR));
    assertThat(instantanea()).isEqualTo(antes);
    var baja =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/archivos/" + id)
                    .with(como(Rol.ESTUDIANTE, AUTOR)))
            .andReturn();
    assertThat(baja.getResponse().getStatus()).isEqualTo(204);
    assertThat(baja.getResponse().getContentAsByteArray()).isEmpty();
    assertThat(objetos()).isEmpty();
    antes = instantanea();
    problema(
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/archivos/" + id)
                    .header("Idempotency-Key", "   ")
                    .with(como(Rol.ESTUDIANTE, AUTOR)))
            .andReturn(),
        404,
        "archivo-no-encontrado");
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  @org.springframework.test.context.jdbc.SqlMergeMode(
      org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE)
  @org.springframework.test.context.jdbc.Sql("/mantenimiento/evidencia-cierre-ordenes.sql")
  @org.springframework.test.context.jdbc.Sql(
      executionPhase = org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD,
      statements = {
        "DELETE FROM auditoria WHERE entidad_id IN (SELECT id FROM archivo WHERE orden_mantenimiento_id IN (SELECT id FROM orden_mantenimiento WHERE puente_id IN ('019a0030-0000-7000-8000-000000000001', '019a0030-0000-7000-8000-000000000002')))",
        "DELETE FROM archivo WHERE orden_mantenimiento_id IN (SELECT id FROM orden_mantenimiento WHERE puente_id IN ('019a0030-0000-7000-8000-000000000001', '019a0030-0000-7000-8000-000000000002'))",
        "DELETE FROM auditoria WHERE entidad_id IN (SELECT id FROM orden_mantenimiento WHERE puente_id IN ('019a0030-0000-7000-8000-000000000001', '019a0030-0000-7000-8000-000000000002'))",
        "DELETE FROM orden_mantenimiento WHERE puente_id IN ('019a0030-0000-7000-8000-000000000001', '019a0030-0000-7000-8000-000000000002')",
        "DELETE FROM inspeccion WHERE id = '019a0030-0000-7000-8000-000000000008'",
        "DELETE FROM formulario_version WHERE id = '019a0030-0000-7000-8000-000000000007'",
        "DELETE FROM puente WHERE id IN ('019a0030-0000-7000-8000-000000000001', '019a0030-0000-7000-8000-000000000002')",
        "DELETE FROM usuario WHERE id = '019a0030-0000-7000-8000-000000000006'",
        "DELETE FROM usuario WHERE correo LIKE '%@cierre-prueba.com'",
        "DELETE FROM municipio WHERE codigo = '9696'",
        "DELETE FROM departamento WHERE codigo = '96'"
      })
  void reservaPdfPendienteImpideFotoEnCualquierPadreYPermiteRecuperarPdf() throws Exception {
    UUID id = UUID.randomUUID();
    String clave = "reserva-pdf-frente-a-foto";
    doAnswer(
            llamada -> {
              throw new IllegalStateException("PUT no iniciado: sonda independiente B1");
            })
        .when(almacenamiento)
        .guardar(anyString(), any(byte[].class), anyString());
    falloTransitorio(() -> subir(false, id, clave, PDF, AUTOR));
    reset(almacenamiento);
    assertThat(
            jdbc.sql(
                    "select count(*) from intento_documento where archivo_id = :id and not finalizada")
                .param("id", id)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(objetos()).isEmpty();
    String objeto =
        jdbc.sql("select clave_objeto from intento_documento where archivo_id = :id")
            .param("id", id)
            .query(String.class)
            .single();
    List<String> antes = estadoCompartido();
    byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0};
    problema(subir(true, id, null, jpeg, AUTOR), 409, "operacion-en-proceso");
    assertThat(estadoCompartido()).isEqualTo(antes);

    // Mismo UUID, otro borrador y su autor legítimo: la reserva no es local al padre.
    var otroPadre =
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                "/api/v1/inspecciones/0199c200-0000-7000-8000-0000000000b5/fotos")
            .file(
                new org.springframework.mock.web.MockMultipartFile(
                    "foto", "foto.jpg", "image/jpeg", jpeg))
            .file(
                new org.springframework.mock.web.MockMultipartFile(
                    "miniatura", "mini.jpg", "image/jpeg", jpeg))
            .param("id", id.toString())
            .param("capturada_en", "2026-10-04T15:30:00Z");
    problema(ejecutar(otroPadre, null, OTRO), 409, "operacion-en-proceso");
    assertThat(estadoCompartido()).isEqualTo(antes);

    var evidencia =
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                "/api/v1/puentes/019a0030-0000-7000-8000-000000000001/ordenes-mantenimiento/019a0030-0000-7000-8000-000000000012/evidencias")
            .file(
                new org.springframework.mock.web.MockMultipartFile(
                    "foto", "foto.jpg", "image/jpeg", jpeg))
            .file(
                new org.springframework.mock.web.MockMultipartFile(
                    "miniatura", "mini.jpg", "image/jpeg", jpeg))
            .param("id", id.toString())
            .param("capturada_en", "2026-10-04T15:30:00Z");
    problema(
        mvc.perform(
                evidencia.with(
                    como(
                        Rol.ADMINISTRADOR,
                        UUID.fromString("019a0030-0000-7000-8000-000000000003"))))
            .andReturn(),
        409,
        "operacion-en-proceso");
    assertThat(estadoCompartido()).isEqualTo(antes);
    org.mockito.Mockito.verify(almacenamiento, org.mockito.Mockito.never())
        .guardar(anyString(), any(byte[].class), anyString());

    var recuperada = subir(false, id, clave, PDF, AUTOR);
    creada(recuperada);
    assertThat(recuperada.getResponse().getContentAsString()).contains("\"tipo\":\"PDF\"");
    assertThat(objetos()).containsExactly(objeto);
    integridad(objeto);
    assertThat(
            jdbc.sql("select count(*) from archivo where id = :id and tipo = 'PDF'")
                .param("id", id)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql("select count(*) from intento_documento where archivo_id = :id and finalizada")
                .param("id", id)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql(
                    "select count(*) from idempotencia where usuario_id = :usuario and clave = :clave and estado_http = 201")
                .param("usuario", AUTOR)
                .param("clave", clave)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    antes = estadoCompartido();
    igual(subir(false, id, clave, PDF, AUTOR), recuperada);
    assertThat(estadoCompartido()).isEqualTo(antes);
  }

  @Test
  void uuidConfirmadoConservaDeduplicacionHistoricaEntreTipos() throws Exception {
    byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0};
    UUID foto = UUID.randomUUID();
    creada(subir(true, foto, null, jpeg, AUTOR));
    List<String> antes = estadoCompartido();
    var pdfSobreFoto = subir(false, foto, null, PDF, AUTOR);
    creada(pdfSobreFoto);
    assertThat(pdfSobreFoto.getResponse().getContentAsString()).contains("\"tipo\":\"JPEG\"");
    assertThat(estadoCompartido()).isEqualTo(antes);
    UUID pdf = UUID.randomUUID();
    creada(subir(false, pdf, "pdf-confirmado", PDF, AUTOR));
    antes = estadoCompartido();
    var fotoSobrePdf = subir(true, pdf, null, jpeg, AUTOR);
    creada(fotoSobrePdf);
    assertThat(fotoSobrePdf.getResponse().getContentAsString()).contains("\"tipo\":\"PDF\"");
    assertThat(estadoCompartido()).isEqualTo(antes);
  }

  @AfterEach
  void limpiarObjetosDeOrdenDelCasoB1() {
    // También limpiar si una regresión llegara a aceptar la evidencia que debía rechazar.
    if (jdbc.sql(
            "select exists(select 1 from orden_mantenimiento where id = '019a0030-0000-7000-8000-000000000012')")
        .query(Boolean.class)
        .single()) {
      try (var s3 = cliente()) {
        var claves =
            s3.listObjectsV2Paginator(o -> o.bucket(AlmacenamientoMinio.BUCKET)).contents().stream()
                .map(o -> o.key())
                .filter(
                    clave ->
                        clave.contains("/019a0030-0000-7000-8000-000000000012/")
                            || clave.contains("/0199c200-0000-7000-8000-0000000000b5/"))
                .toList();
        for (String clave : claves) {
          s3.deleteObject(o -> o.bucket(AlmacenamientoMinio.BUCKET).key(clave));
        }
      }
    }
  }

  /**
   * Incluye filas/counters de ambos padres y mantenimiento, auditoría y bytes/MIME de cada objeto.
   */
  private List<String> estadoCompartido() {
    var estado = new java.util.ArrayList<String>();
    for (String tabla :
        List.of(
            "archivo",
            "inspeccion",
            "orden_mantenimiento",
            "auditoria",
            "idempotencia",
            "intento_documento")) {
      estado.addAll(
          jdbc.sql("select row_to_json(t)::text from " + tabla + " t order by row_to_json(t)::text")
              .query(String.class)
              .list());
    }
    try (var s3 = cliente()) {
      var claves =
          s3.listObjectsV2Paginator(o -> o.bucket(AlmacenamientoMinio.BUCKET)).contents().stream()
              .map(o -> o.key())
              .sorted()
              .toList();
      for (String clave : claves) {
        var contenido = s3.getObjectAsBytes(o -> o.bucket(AlmacenamientoMinio.BUCKET).key(clave));
        estado.add(
            clave
                + ":"
                + contenido.response().contentType()
                + ":"
                + java.util.Base64.getEncoder().encodeToString(contenido.asByteArray()));
      }
    }
    return estado;
  }

  private void esperarBloqueoSql() throws Exception {
    long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < limite) {
      if (jdbc.sql(
              "select exists(select 1 from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock' and query ilike '%inspeccion%')")
          .query(Boolean.class)
          .single()) {
        return;
      }
      Thread.sleep(10);
    }
    throw new AssertionError("La petición no esperó el bloqueo de inspección");
  }

  private String actividad() {
    return jdbc.sql("select ultima_actividad_en::text from inspeccion where id = :id")
        .param("id", PADRE)
        .query(String.class)
        .single();
  }

  private void integridad(String clave) {
    try (var s3 = cliente()) {
      var objeto = s3.getObjectAsBytes(o -> o.bucket(AlmacenamientoMinio.BUCKET).key(clave));
      assertThat(objeto.asByteArray()).isEqualTo(PDF);
      assertThat(objeto.response().contentType()).isEqualTo("application/pdf");
    }
  }

  private static long bloqueoClave(String clave) {
    try {
      byte[] bytes = clave.getBytes(StandardCharsets.UTF_8);
      return ByteBuffer.wrap(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      ByteBuffer.allocate(20 + bytes.length)
                          .putLong(AUTOR.getMostSignificantBits())
                          .putLong(AUTOR.getLeastSignificantBits())
                          .putInt(bytes.length)
                          .put(bytes)
                          .array()))
          .getLong();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void esperar(CountDownLatch permiso) {
    try {
      if (!permiso.await(10, TimeUnit.SECONDS)) {
        throw new AssertionError("No se liberó el bloqueo");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static void falloTransitorio(Peticion peticion) throws Exception {
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
