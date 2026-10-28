package com.cunoc.puentes.archivo;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import com.cunoc.puentes.usuario.Rol;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/** M09: operaciones públicas con PostgreSQL y objetos MinIO reales. */
class ArchivoBajaIdempotenciaHttpTest extends ArchivoIdempotenciaHttpTest {
  private static final UUID OTRO_PADRE = UUID.fromString("0199c200-0000-7000-8000-0000000000b5");
  private static final String ORDEN = "019a0030-0000-7000-8000-000000000012";
  private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0};
  @MockitoSpyBean AlmacenamientoMinio almacenamiento;
  @MockitoSpyBean ArchivoService servicioArchivo;

  @org.springframework.beans.factory.annotation.Autowired
  org.springframework.transaction.PlatformTransactionManager gestor;

  @AfterEach
  void restablecer() {
    reset(almacenamiento, servicioArchivo);
    jdbc.sql("delete from intento_baja_archivo where inspeccion_id in (:id, :otro)")
        .param("id", PADRE)
        .param("otro", OTRO_PADRE)
        .update();
    jdbc.sql("delete from intento_documento where inspeccion_id = :otro")
        .param("otro", OTRO_PADRE)
        .update();
    try (var s3 = cliente()) {
      var claves =
          s3.listObjectsV2Paginator(o -> o.bucket(AlmacenamientoMinio.BUCKET)).contents().stream()
              .map(o -> o.key())
              .filter(c -> c.contains("/" + OTRO_PADRE + "/") || c.contains("/" + ORDEN + "/"))
              .toList();
      for (String clave : claves) {
        s3.deleteObject(o -> o.bucket(AlmacenamientoMinio.BUCKET).key(clave));
      }
    }
  }

  @Test
  void activaReplay204Retenido() throws Exception {
    pendienteM09BajaReproduce204TrasAusenciaSinTocarActividadNiObjetos();
  }

  @Test
  void activaAutorizacionActualRetenida() throws Exception {
    pendienteM09BajaRetenidaRevalidaAutorActualDelPadre();
  }

  @Test
  void replayNoExigeBorradorPeroSiPadreVigenteYRol() throws Exception {
    UUID id = crear(false);
    var original = borrar(id, "tardia", AUTOR);
    vacia(original);
    jdbc.sql("update inspeccion set estado = 'ENVIADA' where id = :id").param("id", PADRE).update();
    var antes = estado();
    igual(borrar(id, "tardia", AUTOR), original);
    assertThat(estado()).isEqualTo(antes);
    problema(borrar(id, "tardia", OTRO), 404, "archivo-no-encontrado");
    assertThat(estado()).isEqualTo(antes);
    assertThat(
            mvc.perform(
                    delete("/api/v1/archivos/" + id)
                        .header("Idempotency-Key", "tardia")
                        .with(como(Rol.ADMINISTRADOR, AUTOR)))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(403);
    assertThat(estado()).isEqualTo(antes);
    jdbc.sql("update inspeccion set eliminado_en = now() where id = :id")
        .param("id", PADRE)
        .update();
    antes = estado();
    problema(borrar(id, "tardia", AUTOR), 404, "inspeccion-no-encontrada");
    assertThat(estado()).isEqualTo(antes);
  }

  @Test
  void claveNoSeReutilizaParaOtroArchivoExistenteNiCarga() throws Exception {
    UUID uno = crear(false);
    UUID dos = crear(true);
    vacia(borrar(uno, "compartida", AUTOR));
    var antes = estado();
    problema(borrar(dos, "compartida", AUTOR), 409, "clave-idempotencia-incompatible");
    assertThat(estado()).isEqualTo(antes);
    problema(
        subir(false, UUID.randomUUID(), "compartida", PDF, AUTOR),
        409,
        "clave-idempotencia-incompatible");
    assertThat(estado()).isEqualTo(antes);
  }

  @Test
  void clavesInvalidasYRechazoNoEditableNoSeConservan() throws Exception {
    UUID id = crear(false);
    var antes = estado();
    problema(borrar(id, "😀".repeat(101), AUTOR), 400, "idempotencia-invalida");
    assertThat(estado()).isEqualTo(antes);
    jdbc.sql("update inspeccion set estado = 'ENVIADA' where id = :id").param("id", PADRE).update();
    antes = estado();
    problema(borrar(id, "corregible", AUTOR), 409, "inspeccion-no-editable");
    assertThat(estado()).isEqualTo(antes);
    jdbc.sql("update inspeccion set estado = 'BORRADOR' where id = :id")
        .param("id", PADRE)
        .update();
    var original = borrar(id, " corregible ", AUTOR);
    vacia(original);
    igual(borrar(id, "corregible", AUTOR), original);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void deleteInciertoReconciliadoNoRepiteEfectos(boolean foto) throws Exception {
    UUID id = crear(foto);
    AtomicInteger llamadas = new AtomicInteger();
    doAnswer(
            invocacion -> {
              invocacion.callRealMethod();
              if (llamadas.incrementAndGet() == 1) {
                throw new IllegalStateException("Respuesta perdida tras DELETE real");
              }
              return null;
            })
        .when(almacenamiento)
        .eliminar(anyString());
    fallo(() -> borrar(id, "incierta", AUTOR));
    assertThat(objetos()).hasSize(foto ? 1 : 0);
    var resultado = borrar(id, "incierta", AUTOR);
    vacia(resultado);
    assertThat(objetos()).isEmpty();
    assertThat(llamadas.get()).isEqualTo(foto ? 2 : 1);
    var antes = estado();
    igual(borrar(id, "incierta", AUTOR), resultado);
    assertThat(estado()).isEqualTo(antes);
    assertThat(llamadas.get()).isEqualTo(foto ? 2 : 1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rollbackFinalNoPierdeIdentidadNiRepiteDeletes(boolean foto) throws Exception {
    UUID id = crear(foto);
    String actividad =
        jdbc.sql("select ultima_actividad_en::text from inspeccion where id = :id")
            .param("id", PADRE)
            .query(String.class)
            .single();
    jdbc.sql(
            "alter table idempotencia add constraint prueba_baja_resultado check (clave <> 'rollback-baja')")
        .update();
    try {
      fallo(() -> borrar(id, "rollback-baja", AUTOR));
    } finally {
      jdbc.sql("alter table idempotencia drop constraint prueba_baja_resultado").update();
    }
    assertThat(objetos()).isEmpty();
    assertThat(
            jdbc.sql("select count(*) from archivo where id = :id")
                .param("id", id)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql("select ultima_actividad_en::text from inspeccion where id = :id")
                .param("id", PADRE)
                .query(String.class)
                .single())
        .isEqualTo(actividad);
    clearInvocations(almacenamiento);
    vacia(borrar(id, "rollback-baja", AUTOR));
    verify(almacenamiento, never()).eliminar(anyString());
  }

  @Test
  void replayAntiguoNoBorraNuevaEncarnacionDelUuid() throws Exception {
    UUID id = crear(false);
    var original = borrar(id, "vieja", AUTOR);
    vacia(original);
    creada(subir(true, id, null, JPEG, AUTOR));
    var antes = estado();
    igual(borrar(id, "vieja", AUTOR), original);
    assertThat(estado()).isEqualTo(antes);
    assertThat(objetos()).hasSize(2);
    vacia(borrar(id, "nueva", AUTOR));
    assertThat(objetos()).isEmpty();
  }

  @Test
  void duplicadosConcurrentesReproducenUn204() throws Exception {
    UUID id = crear(true);
    CountDownLatch inicio = new CountDownLatch(1);
    try (var hilos = Executors.newFixedThreadPool(2)) {
      var uno =
          hilos.submit(
              () -> {
                inicio.await();
                return borrar(id, "concurrente-baja", AUTOR);
              });
      var dos =
          hilos.submit(
              () -> {
                inicio.await();
                return borrar(id, "concurrente-baja", AUTOR);
              });
      inicio.countDown();
      var original = uno.get(20, TimeUnit.SECONDS);
      vacia(original);
      igual(dos.get(20, TimeUnit.SECONDS), original);
    }
    assertThat(objetos()).isEmpty();
    assertThat(
            jdbc.sql(
                    "select count(*) from idempotencia where usuario_id = :id and clave = 'concurrente-baja'")
                .param("id", AUTOR)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void sinClaveYBlancaConservan204Luego404() throws Exception {
    for (String clave : new String[] {null, "   "}) {
      UUID id = crear(false);
      vacia(borrar(id, clave, AUTOR));
      var antes = estado();
      problema(borrar(id, clave, AUTOR), 404, "archivo-no-encontrado");
      assertThat(estado()).isEqualTo(antes);
    }
  }

  @Test
  void bajaPendienteBloqueaOtrosEscritoresYClavesSinCambiarObjetos() throws Exception {
    UUID id = crear(true);
    doAnswer(
            invocacion -> {
              throw new IllegalStateException("DELETE no iniciado");
            })
        .when(almacenamiento)
        .eliminar(anyString());
    fallo(() -> borrar(id, "reserva-baja", AUTOR));
    reset(almacenamiento);
    var antes = estado();
    problema(borrar(id, "otra-baja", AUTOR), 409, "operacion-en-proceso");
    assertThat(estado()).isEqualTo(antes);
    problema(borrar(id, null, AUTOR), 409, "operacion-en-proceso");
    assertThat(estado()).isEqualTo(antes);
    problema(subir(false, id, null, PDF, AUTOR), 409, "operacion-en-proceso");
    assertThat(estado()).isEqualTo(antes);
    problema(subir(true, id, "otra-foto", JPEG, AUTOR), 409, "operacion-en-proceso");
    assertThat(estado()).isEqualTo(antes);
    problema(
        subir(false, UUID.randomUUID(), "reserva-baja", PDF, AUTOR),
        409,
        "clave-idempotencia-incompatible");
    assertThat(estado()).isEqualTo(antes);
    vacia(borrar(id, "reserva-baja", AUTOR));
    assertThat(objetos()).isEmpty();
  }

  @Test
  void falloAntesDeMiniaturaConservaManifiestoYCompletaSoloLaFaltante() throws Exception {
    UUID id = crear(true);
    AtomicInteger llamadas = new AtomicInteger();
    doAnswer(
            invocacion -> {
              if (llamadas.incrementAndGet() == 2) {
                throw new IllegalStateException("DELETE de miniatura no iniciado");
              }
              return invocacion.callRealMethod();
            })
        .when(almacenamiento)
        .eliminar(anyString());
    fallo(() -> borrar(id, "miniatura-pendiente", AUTOR));
    assertThat(objetos()).hasSize(1);
    reset(almacenamiento);
    vacia(borrar(id, "miniatura-pendiente", AUTOR));
    assertThat(objetos()).isEmpty();
    verify(almacenamiento).eliminar(anyString());
  }

  @Test
  void esperaClaveAgotadaNoRevelaReplayNiCambiaEfectos() throws Exception {
    UUID id = crear(false);
    vacia(borrar(id, "espera-baja", AUTOR));
    CountDownLatch bloqueado = new CountDownLatch(1);
    CountDownLatch liberar = new CountDownLatch(1);
    try (var hilos = Executors.newFixedThreadPool(2)) {
      var bloqueo =
          hilos.submit(
              () ->
                  new org.springframework.transaction.support.TransactionTemplate(gestor)
                      .execute(
                          estado -> {
                            jdbc.sql("select pg_advisory_xact_lock(:clave)")
                                .param("clave", bloqueoClave("espera-baja"))
                                .query(Object.class)
                                .single();
                            bloqueado.countDown();
                            esperar(liberar);
                            return true;
                          }));
      assertThat(bloqueado.await(5, TimeUnit.SECONDS)).isTrue();
      var antes = estado();
      try {
        problema(borrar(id, "espera-baja", AUTOR), 409, "operacion-en-proceso");
        assertThat(estado()).isEqualTo(antes);
      } finally {
        liberar.countDown();
      }
      bloqueo.get(10, TimeUnit.SECONDS);
    }
  }

  @Test
  void reautorizaDespuesDeEsperarClaveDeReplay() throws Exception {
    UUID id = crear(false);
    vacia(borrar(id, "espera-autor", AUTOR));
    CountDownLatch bloqueado = new CountDownLatch(1);
    CountDownLatch liberar = new CountDownLatch(1);
    CountDownLatch autorizado = new CountDownLatch(1);
    CountDownLatch continuar = new CountDownLatch(1);
    AtomicInteger comprobaciones = new AtomicInteger();
    doAnswer(
            invocacion -> {
              Object resultado = invocacion.callRealMethod();
              if (comprobaciones.incrementAndGet() == 1) {
                autorizado.countDown();
                esperar(continuar);
              }
              return resultado;
            })
        .when(servicioArchivo)
        .inspeccionPropia(PADRE, AUTOR);
    try (var hilos = Executors.newFixedThreadPool(2)) {
      var bloqueo =
          hilos.submit(
              () ->
                  new org.springframework.transaction.support.TransactionTemplate(gestor)
                      .execute(
                          estado -> {
                            jdbc.sql("select pg_advisory_xact_lock(:clave)")
                                .param("clave", bloqueoClave("espera-autor"))
                                .query(Object.class)
                                .single();
                            bloqueado.countDown();
                            esperar(liberar);
                            return true;
                          }));
      assertThat(bloqueado.await(5, TimeUnit.SECONDS)).isTrue();
      var peticion = hilos.submit(() -> borrar(id, "espera-autor", AUTOR));
      try {
        assertThat(autorizado.await(5, TimeUnit.SECONDS)).isTrue();
        jdbc.sql("update inspeccion set autor_id = :otro where id = :id")
            .param("otro", OTRO)
            .param("id", PADRE)
            .update();
      } finally {
        continuar.countDown();
        liberar.countDown();
      }
      bloqueo.get(10, TimeUnit.SECONDS);
      problema(peticion.get(10, TimeUnit.SECONDS), 404, "inspeccion-no-encontrada");
    }
  }

  @Test
  void mismaClaveDeUsuariosAutorizadosNoComparteIdentidadNiResultado() throws Exception {
    UUID uno = crear(false);
    UUID dos = UUID.randomUUID();
    creada(subirEnOtroPadre(false, dos, null));
    var primera = borrar(uno, "clave-por-usuario", AUTOR);
    vacia(primera);
    var antes = estado();
    problema(borrar(uno, "clave-por-usuario", OTRO), 404, "archivo-no-encontrado");
    assertThat(estado()).isEqualTo(antes);
    var segunda = borrar(dos, "clave-por-usuario", OTRO);
    vacia(segunda);
    antes = estado();
    igual(borrar(uno, "clave-por-usuario", AUTOR), primera);
    igual(borrar(dos, "clave-por-usuario", OTRO), segunda);
    assertThat(estado()).isEqualTo(antes);
    assertThat(
            jdbc.sql("select count(*) from idempotencia where clave = 'clave-por-usuario'")
                .query(Long.class)
                .single())
        .isEqualTo(2);
  }

  @Test
  void reincarnacionEnOtroPadreNoSustituyeAutorizacionRetenida() throws Exception {
    UUID id = crear(false);
    var original = borrar(id, "padre-original", AUTOR);
    vacia(original);
    creada(subirEnOtroPadre(true, id, null));
    var antes = estado();
    // El autor original ya no es dueño de la fila actual, pero conserva acceso al padre retenido.
    igual(borrar(id, "padre-original", AUTOR), original);
    assertThat(estado()).isEqualTo(antes);
    // Poseer la nueva fila no concede acceso a la operación del autor anterior.
    vacia(borrar(id, "padre-original", OTRO));
    creada(subirEnOtroPadre(false, id, null));
    // Ahora el autor original sí posee la fila nueva; perdió únicamente el padre antiguo.
    jdbc.sql("update inspeccion set autor_id = :autor where id = :id")
        .param("autor", AUTOR)
        .param("id", OTRO_PADRE)
        .update();
    jdbc.sql("update inspeccion set autor_id = :autor where id = :id")
        .param("autor", OTRO)
        .param("id", PADRE)
        .update();
    antes = estado();
    problema(borrar(id, "padre-original", AUTOR), 404, "inspeccion-no-encontrada");
    assertThat(estado()).isEqualTo(antes);
    vacia(borrar(id, "nueva-encarnacion", AUTOR));
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {
        "baja-clave",
        "baja-sin-clave",
        "baja-blanca",
        "foto",
        "pdf",
        "otro-padre",
        "mantenimiento"
      })
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
  void bajaEnVueloReservaUuidHastaQueElCompetidorPuedeRevalidar(String competidor)
      throws Exception {
    UUID id = crear(true);
    CountDownLatch externo = new CountDownLatch(1);
    CountDownLatch fallar = new CountDownLatch(1);
    doAnswer(
            invocacion -> {
              externo.countDown();
              esperar(fallar);
              throw new IllegalStateException("DELETE no salió: intención ya confirmada");
            })
        .when(almacenamiento)
        .eliminar(anyString());
    try (var hilos = Executors.newFixedThreadPool(2)) {
      var original =
          hilos.submit(
              () -> {
                fallo(() -> borrar(id, "reserva-en-vuelo", AUTOR));
                return true;
              });
      assertThat(externo.await(5, TimeUnit.SECONDS)).isTrue();
      var antes = estado();
      var otra = hilos.submit(() -> competir(competidor, id));
      try {
        esperarCompetidorSql();
      } finally {
        fallar.countDown();
      }
      assertThat(original.get(10, TimeUnit.SECONDS)).isTrue();
      problema(otra.get(10, TimeUnit.SECONDS), 409, "operacion-en-proceso");
      assertThat(estado()).isEqualTo(antes);
    } finally {
      fallar.countDown();
    }
    reset(almacenamiento);
    vacia(borrar(id, "reserva-en-vuelo", AUTOR));
    assertThat(objetos()).isEmpty();
  }

  @Test
  void purgaConservaReservaPendienteYPurgaHistoricaSigueFuncionando() throws Exception {
    UUID id = crear(true);
    doAnswer(
            invocacion -> {
              throw new IllegalStateException("DELETE no iniciado");
            })
        .when(almacenamiento)
        .eliminar(anyString());
    fallo(() -> borrar(id, "reserva-purga", AUTOR));
    reset(almacenamiento);
    jdbc.sql("update inspeccion set actualizado_en = now() - interval '211 days' where id = :id")
        .param("id", PADRE)
        .update();
    var antes = estado();
    // No hay endpoint de purga: se verifica su entrada pública programada, no almacenamiento
    // interno.
    ejecutarSiguienteCorridaDePurga();
    assertThat(estado()).isEqualTo(antes);
    vacia(borrar(id, "reserva-purga", AUTOR));
    UUID historico = crear(true);
    jdbc.sql("update inspeccion set actualizado_en = now() - interval '211 days' where id = :id")
        .param("id", PADRE)
        .update();
    ejecutarSiguienteCorridaDePurga();
    assertThat(objetos()).isEmpty();
    assertThat(
            jdbc.sql("select purgado_en is not null from archivo where id = :id")
                .param("id", historico)
                .query(Boolean.class)
                .single())
        .isTrue();
  }

  private void ejecutarSiguienteCorridaDePurga() {
    // Simula cron sucesivos, sin depender de cuánto retenga ShedLock la corrida anterior.
    jdbc.sql(
            """
            update shedlock
            set lock_until = timezone('utc', clock_timestamp()) - interval '1 second'
            where name = 'purga-de-archivos'
            """)
        .update();
    servicioArchivo.purgarBorradoresAbandonados();
  }

  private MvcResult competir(String competidor, UUID id) throws Exception {
    return switch (competidor) {
      case "baja-clave" -> borrar(id, "competidora", AUTOR);
      case "baja-sin-clave" -> borrar(id, null, AUTOR);
      case "baja-blanca" -> borrar(id, "   ", AUTOR);
      case "foto" -> subir(true, id, null, JPEG, AUTOR);
      case "pdf" -> subir(false, id, "competidor-pdf", PDF, AUTOR);
      case "otro-padre" -> subirEnOtroPadre(true, id, null);
      case "mantenimiento" ->
          mvc.perform(
                  org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                          "/api/v1/puentes/019a0030-0000-7000-8000-000000000001/ordenes-mantenimiento/"
                              + ORDEN
                              + "/evidencias")
                      .file(
                          new org.springframework.mock.web.MockMultipartFile(
                              "foto", "foto.jpg", "image/jpeg", JPEG))
                      .file(
                          new org.springframework.mock.web.MockMultipartFile(
                              "miniatura", "mini.jpg", "image/jpeg", JPEG))
                      .param("id", id.toString())
                      .param("capturada_en", "2026-10-04T15:30:00Z")
                      .with(
                          como(
                              Rol.ADMINISTRADOR,
                              UUID.fromString("019a0030-0000-7000-8000-000000000003"))))
              .andReturn();
      default -> throw new AssertionError("Competidor no previsto");
    };
  }

  private MvcResult subirEnOtroPadre(boolean foto, UUID id, String clave) throws Exception {
    var peticion =
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                "/api/v1/inspecciones/" + OTRO_PADRE + (foto ? "/fotos" : "/documentos"))
            .file(
                new org.springframework.mock.web.MockMultipartFile(
                    foto ? "foto" : "pdf",
                    "cliente",
                    foto ? "image/jpeg" : "application/pdf",
                    foto ? JPEG : PDF))
            .param("id", id.toString());
    if (foto) {
      peticion
          .file(
              new org.springframework.mock.web.MockMultipartFile(
                  "miniatura", "mini.jpg", "image/jpeg", JPEG))
          .param("capturada_en", "2026-10-04T15:30:00Z");
    }
    return ejecutar(peticion, clave, OTRO);
  }

  private void esperarCompetidorSql() {
    long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < limite) {
      if (jdbc.sql(
              """
          select exists(select 1 from pg_stat_activity
            where datname = current_database() and wait_event_type = 'Lock'
              and cardinality(pg_blocking_pids(pid)) > 0)
          """)
          .query(Boolean.class)
          .single()) {
        return;
      }
      Thread.onSpinWait();
    }
    throw new AssertionError("El competidor no alcanzó un bloqueo SQL real");
  }

  private static long bloqueoClave(String clave) {
    try {
      byte[] bytes = clave.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      return java.nio.ByteBuffer.wrap(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(
                      java.nio.ByteBuffer.allocate(20 + bytes.length)
                          .putLong(AUTOR.getMostSignificantBits())
                          .putLong(AUTOR.getLeastSignificantBits())
                          .putInt(bytes.length)
                          .put(bytes)
                          .array()))
          .getLong();
    } catch (java.security.NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
  }

  private static void esperar(CountDownLatch permiso) {
    try {
      if (!permiso.await(10, TimeUnit.SECONDS)) {
        throw new AssertionError("No se liberó el bloqueo");
      }
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(error);
    }
  }

  private UUID crear(boolean foto) throws Exception {
    UUID id = UUID.randomUUID();
    creada(subir(foto, id, null, foto ? JPEG : PDF, AUTOR));
    return id;
  }

  private static void vacia(MvcResult respuesta) {
    assertThat(respuesta.getResponse().getStatus()).isEqualTo(204);
    assertThat(respuesta.getResponse().getContentAsByteArray()).isEmpty();
    assertThat(respuesta.getResponse().getContentType()).isNull();
  }

  /** Filas completas incluyen actividad, contadores, auditoría y bytes/MIME reales. */
  private List<String> estado() {
    var resultado = new ArrayList<String>();
    for (String tabla :
        List.of(
            "archivo",
            "inspeccion",
            "orden_mantenimiento",
            "auditoria",
            "idempotencia",
            "intento_documento",
            "intento_baja_archivo")) {
      resultado.addAll(
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
        var objeto = s3.getObjectAsBytes(o -> o.bucket(AlmacenamientoMinio.BUCKET).key(clave));
        resultado.add(
            clave
                + ":"
                + objeto.response().contentType()
                + ":"
                + Base64.getEncoder().encodeToString(objeto.asByteArray()));
      }
    }
    return resultado;
  }

  private static void fallo(Peticion peticion) throws Exception {
    try {
      assertThat(peticion.ejecutar().getResponse().getStatus()).isEqualTo(500);
    } catch (jakarta.servlet.ServletException error) {
      assertThat(error.getCause()).isInstanceOf(RuntimeException.class);
    }
  }

  @FunctionalInterface
  private interface Peticion {
    MvcResult ejecutar() throws Exception;
  }
}
