package com.cunoc.puentes.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Contrato público del núcleo común de idempotencia contra PostgreSQL real. */
@SpringBootTest(classes = IdempotenciaServiceTest.ConfiguracionDePruebas.class)
class IdempotenciaServiceTest extends PruebaIntegracion {

  private static final String TIPO_INCOMPATIBLE =
      "https://sgp.cunoc.usac.edu.gt/errores/clave-idempotencia-incompatible";
  private static final String TIPO_EN_PROCESO =
      "https://sgp.cunoc.usac.edu.gt/errores/operacion-en-proceso";

  @Autowired private IdempotenciaService servicio;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager gestorTransacciones;

  private final List<UUID> usuarios = new ArrayList<>();
  private UUID usuarioId;

  @BeforeEach
  void prepararUsuario() {
    usuarios.clear();
    usuarioId = crearUsuario();
  }

  @AfterEach
  void limpiarUsuarios() {
    usuarios.forEach(id -> jdbc.update("DELETE FROM idempotencia WHERE usuario_id = ?", id));
    usuarios.forEach(id -> jdbc.update("DELETE FROM usuario WHERE id = ?", id));
  }

  @Test
  @DisplayName("reproduce exactamente bytes vacíos, espacios y cuerpos que no son JSON")
  void replayConservaStatusYBytesSinInterpretarlos() {
    byte[] bytesConEspacios = " \n{esto no es JSON}\t ".getBytes(StandardCharsets.UTF_8);
    RespuestaIdempotente primera = new RespuestaIdempotente(207, bytesConEspacios);
    bytesConEspacios[0] = 'X';

    RespuestaIdempotente guardada =
        servicio.ejecutar(
            usuarioId, "respuesta-whitespace", huella(1, 3), Duration.ofSeconds(1), () -> primera);
    RespuestaIdempotente replay =
        servicio.ejecutar(
            usuarioId,
            "respuesta-whitespace",
            huella(1, 3),
            Duration.ofSeconds(1),
            () -> {
              throw new AssertionError("el callback no debe ejecutarse durante replay");
            });

    assertThat(guardada.estadoHttp()).isEqualTo(207);
    assertThat(guardada.cuerpo())
        .containsExactly(" \n{esto no es JSON}\t ".getBytes(StandardCharsets.UTF_8));
    assertThat(replay.estadoHttp()).isEqualTo(207);
    assertThat(replay.cuerpo()).containsExactly(guardada.cuerpo());

    RespuestaIdempotente vacia =
        servicio.ejecutar(
            usuarioId,
            "respuesta-vacia",
            huella(1, 4),
            Duration.ofSeconds(1),
            () -> new RespuestaIdempotente(204, new byte[0]));
    RespuestaIdempotente replayVacio =
        servicio.ejecutar(
            usuarioId,
            "respuesta-vacia",
            huella(1, 4),
            Duration.ofSeconds(1),
            () -> {
              throw new AssertionError("el callback no debe ejecutarse durante replay vacío");
            });

    assertThat(vacia.estadoHttp()).isEqualTo(204);
    assertThat(vacia.cuerpo()).isEmpty();
    assertThat(replayVacio.estadoHttp()).isEqualTo(204);
    assertThat(replayVacio.cuerpo()).isEmpty();
  }

  @Test
  @DisplayName("rechaza otra huella o versión sin ejecutar ni reemplazar el resultado original")
  void huellaIncompatibleNoEjecutaNiSobrescribe() {
    AtomicInteger ejecuciones = new AtomicInteger();
    RespuestaIdempotente original = new RespuestaIdempotente(201, new byte[] {1, 2, 3});
    servicio.ejecutar(
        usuarioId,
        "misma-clave-huella",
        huella(1, 10),
        Duration.ofSeconds(1),
        () -> {
          ejecuciones.incrementAndGet();
          return original;
        });

    assertConflicto(TIPO_INCOMPATIBLE, "misma-clave-huella", huella(1, 11), ejecuciones);
    assertThat(
            servicio
                .ejecutar(
                    usuarioId,
                    "misma-clave-huella",
                    huella(1, 10),
                    Duration.ofSeconds(1),
                    () -> {
                      ejecuciones.incrementAndGet();
                      return new RespuestaIdempotente(500, new byte[] {9});
                    })
                .cuerpo())
        .containsExactly(1, 2, 3);

    servicio.ejecutar(
        usuarioId,
        "misma-clave-version",
        huella(1, 12),
        Duration.ofSeconds(1),
        () -> {
          ejecuciones.incrementAndGet();
          return original;
        });
    assertConflicto(TIPO_INCOMPATIBLE, "misma-clave-version", huella(2, 12), ejecuciones);
    assertThat(ejecuciones).hasValue(2);
  }

  @Test
  @DisplayName("una misma clave queda aislada por usuario")
  void claveSeAíslaPorUsuario() {
    UUID otroUsuario = crearUsuario();
    AtomicInteger ejecuciones = new AtomicInteger();

    RespuestaIdempotente primera =
        servicio.ejecutar(
            usuarioId,
            "clave-compartida",
            huella(1, 20),
            Duration.ofSeconds(1),
            () -> {
              ejecuciones.incrementAndGet();
              return new RespuestaIdempotente(201, new byte[] {1});
            });
    RespuestaIdempotente segunda =
        servicio.ejecutar(
            otroUsuario,
            "clave-compartida",
            huella(1, 20),
            Duration.ofSeconds(1),
            () -> {
              ejecuciones.incrementAndGet();
              return new RespuestaIdempotente(202, new byte[] {2});
            });

    assertThat(primera.estadoHttp()).isEqualTo(201);
    assertThat(segunda.estadoHttp()).isEqualTo(202);
    assertThat(ejecuciones).hasValue(2);
    assertThat(
            servicio
                .ejecutar(
                    usuarioId,
                    "clave-compartida",
                    huella(1, 20),
                    Duration.ofSeconds(1),
                    () -> {
                      throw new AssertionError("replay debe respetar al usuario original");
                    })
                .cuerpo())
        .containsExactly(1);
    assertThat(
            servicio
                .ejecutar(
                    otroUsuario,
                    "clave-compartida",
                    huella(1, 20),
                    Duration.ofSeconds(1),
                    () -> {
                      throw new AssertionError("replay debe respetar al segundo usuario");
                    })
                .cuerpo())
        .containsExactly(2);
  }

  @Test
  @DisplayName("una excepción del callback revierte sus escrituras y permite reintentar")
  void excepcionRevierteEfectosYPermiteReintentar() {
    String nombreOriginal = nombreUsuario(usuarioId);

    assertThatThrownBy(
            () ->
                servicio.ejecutar(
                    usuarioId,
                    "rollback-callback",
                    huella(1, 30),
                    Duration.ofSeconds(1),
                    () -> {
                      jdbc.update(
                          "UPDATE usuario SET nombre = ? WHERE id = ?",
                          "cambio no confirmado",
                          usuarioId);
                      throw new IllegalStateException("fallo de negocio");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("fallo de negocio");

    assertThat(nombreUsuario(usuarioId)).isEqualTo(nombreOriginal);

    assertThatThrownBy(
            () ->
                servicio.ejecutar(
                    usuarioId,
                    "rollback-callback",
                    huella(1, 30),
                    Duration.ofSeconds(1),
                    () -> {
                      jdbc.update(
                          "UPDATE usuario SET nombre = ? WHERE id = ?",
                          "resultado nulo no confirmado",
                          usuarioId);
                      return null;
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("El callback debe devolver una respuesta admitida.");
    assertThat(nombreUsuario(usuarioId)).isEqualTo(nombreOriginal);

    AtomicInteger ejecuciones = new AtomicInteger();
    RespuestaIdempotente confirmada =
        servicio.ejecutar(
            usuarioId,
            "rollback-callback",
            huella(1, 30),
            Duration.ofSeconds(1),
            () -> {
              ejecuciones.incrementAndGet();
              jdbc.update("UPDATE usuario SET nombre = ? WHERE id = ?", "confirmado", usuarioId);
              return new RespuestaIdempotente(200, new byte[] {7, 8});
            });
    RespuestaIdempotente replay =
        servicio.ejecutar(
            usuarioId,
            "rollback-callback",
            huella(1, 30),
            Duration.ofSeconds(1),
            () -> {
              throw new AssertionError("no se repiten los efectos confirmados");
            });

    assertThat(nombreUsuario(usuarioId)).isEqualTo("confirmado");
    assertThat(confirmada.cuerpo()).containsExactly(7, 8);
    assertThat(replay.cuerpo()).containsExactly(7, 8);
    assertThat(ejecuciones).hasValue(1);
  }

  @Test
  @DisplayName("dos llamadas concurrentes ejecutan un solo callback y la segunda reproduce")
  void llamadasConcurrentesEjecutanUnaVez() throws Exception {
    ExecutorService ejecutor = Executors.newFixedThreadPool(2);
    CountDownLatch callbackDentro = new CountDownLatch(1);
    CountDownLatch liberarCallback = new CountDownLatch(1);
    CountDownLatch segundaIniciada = new CountDownLatch(1);
    AtomicInteger ejecuciones = new AtomicInteger();

    try {
      Future<RespuestaIdempotente> primera =
          ejecutor.submit(
              () ->
                  servicio.ejecutar(
                      usuarioId,
                      "concurrencia",
                      huella(1, 40),
                      Duration.ofSeconds(3),
                      () -> {
                        ejecuciones.incrementAndGet();
                        callbackDentro.countDown();
                        esperar(liberarCallback);
                        return new RespuestaIdempotente(201, new byte[] {4, 0, 4});
                      }));
      assertThat(callbackDentro.await(3, TimeUnit.SECONDS)).isTrue();

      Future<RespuestaIdempotente> segunda =
          ejecutor.submit(
              () -> {
                segundaIniciada.countDown();
                return servicio.ejecutar(
                    usuarioId,
                    "concurrencia",
                    huella(1, 40),
                    Duration.ofSeconds(3),
                    () -> {
                      ejecuciones.incrementAndGet();
                      return new RespuestaIdempotente(500, new byte[] {5});
                    });
              });
      assertThat(segundaIniciada.await(3, TimeUnit.SECONDS)).isTrue();
      Thread.sleep(100);
      liberarCallback.countDown();

      assertThat(primera.get(5, TimeUnit.SECONDS).cuerpo()).containsExactly(4, 0, 4);
      assertThat(segunda.get(5, TimeUnit.SECONDS).estadoHttp()).isEqualTo(201);
      assertThat(segunda.get(5, TimeUnit.SECONDS).cuerpo()).containsExactly(4, 0, 4);
      assertThat(ejecuciones).hasValue(1);
    } finally {
      liberarCallback.countDown();
      ejecutor.shutdownNow();
      assertThat(ejecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  @DisplayName("el timeout devuelve 409 no cacheado y la misma operación se puede reintentar")
  void esperaAcotadaNoSeCacheaYPermiteReintentar() throws Exception {
    ExecutorService ejecutor = Executors.newSingleThreadExecutor();
    CountDownLatch callbackDentro = new CountDownLatch(1);
    CountDownLatch liberarCallback = new CountDownLatch(1);
    AtomicInteger ejecuciones = new AtomicInteger();

    try {
      Future<RespuestaIdempotente> primera =
          ejecutor.submit(
              () ->
                  servicio.ejecutar(
                      usuarioId,
                      "timeout-reintentable",
                      huella(1, 50),
                      Duration.ofSeconds(3),
                      () -> {
                        ejecuciones.incrementAndGet();
                        callbackDentro.countDown();
                        esperar(liberarCallback);
                        return new RespuestaIdempotente(201, new byte[] {5, 0, 5});
                      }));
      assertThat(callbackDentro.await(3, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(
              () ->
                  servicio.ejecutar(
                      usuarioId,
                      "timeout-reintentable",
                      huella(1, 50),
                      Duration.ofMillis(100),
                      () -> {
                        ejecuciones.incrementAndGet();
                        return new RespuestaIdempotente(500, new byte[] {9});
                      }))
          .isInstanceOfSatisfying(
              NegocioException.class,
              error -> {
                assertThat(error.getStatusCode().value()).isEqualTo(409);
                assertThat(error.getBody().getType()).isEqualTo(URI.create(TIPO_EN_PROCESO));
              });
      assertThat(ejecuciones).hasValue(1);
      liberarCallback.countDown();

      RespuestaIdempotente primeraConfirmada = primera.get(5, TimeUnit.SECONDS);
      RespuestaIdempotente replay =
          servicio.ejecutar(
              usuarioId,
              "timeout-reintentable",
              huella(1, 50),
              Duration.ofSeconds(1),
              () -> {
                ejecuciones.incrementAndGet();
                return new RespuestaIdempotente(500, new byte[] {10});
              });

      assertThat(primeraConfirmada.cuerpo()).containsExactly(5, 0, 5);
      assertThat(replay.estadoHttp()).isEqualTo(201);
      assertThat(replay.cuerpo()).containsExactly(5, 0, 5);
      assertThat(ejecuciones).hasValue(1);
    } finally {
      liberarCallback.countDown();
      ejecutor.shutdownNow();
      assertThat(ejecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  @DisplayName("filas heredadas con JSONB escalar o nulo se rechazan sin ejecutar el callback")
  void filasHeredadasNoSeReejecutanNiSeReinterpretan() {
    jdbc.update(
        """
        INSERT INTO idempotencia (id, usuario_id, clave, estado_http, respuesta)
        VALUES (?, ?, ?, ?, CAST(? AS jsonb))
        """,
        UUID.randomUUID(),
        usuarioId,
        "clave-heredada-escalar",
        201,
        "\"respuesta heredada\"");
    jdbc.update(
        """
        INSERT INTO idempotencia (id, usuario_id, clave, estado_http, respuesta)
        VALUES (?, ?, ?, ?, NULL)
        """,
        UUID.randomUUID(),
        usuarioId,
        "clave-heredada-nula",
        202);
    AtomicBoolean callbackEjecutado = new AtomicBoolean();

    for (String clave : List.of("clave-heredada-escalar", "clave-heredada-nula")) {
      assertThatThrownBy(
              () ->
                  servicio.ejecutar(
                      usuarioId,
                      clave,
                      huella(1, 60),
                      Duration.ofSeconds(1),
                      () -> {
                        callbackEjecutado.set(true);
                        return new RespuestaIdempotente(201, new byte[] {6});
                      }))
          .isInstanceOfSatisfying(
              NegocioException.class,
              error -> {
                assertThat(error.getStatusCode().value()).isEqualTo(409);
                assertThat(error.getBody().getType()).isEqualTo(URI.create(TIPO_INCOMPATIBLE));
              });
    }

    assertThat(callbackEjecutado).isFalse();
  }

  @Test
  @DisplayName("los tipos de valor copian bytes y validan los límites públicos")
  void valoresValidanYCopianBytesDefensivamente() {
    byte[] digestOriginal = digest(70);
    HuellaIdempotencia huella = new HuellaIdempotencia(3, digestOriginal);
    digestOriginal[0] = 99;
    byte[] digestLeido = huella.sha256();
    digestLeido[1] = 99;
    assertThat(huella.sha256()).containsExactly(digest(70));

    byte[] cuerpoOriginal = {7, 0, 7};
    RespuestaIdempotente respuesta = new RespuestaIdempotente(299, cuerpoOriginal);
    cuerpoOriginal[0] = 99;
    byte[] cuerpoLeido = respuesta.cuerpo();
    cuerpoLeido[1] = 99;
    assertThat(respuesta.cuerpo()).containsExactly(7, 0, 7);

    assertThatThrownBy(() -> new HuellaIdempotencia(0, digest(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new HuellaIdempotencia(1, new byte[31]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RespuestaIdempotente(99, new byte[0]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RespuestaIdempotente(600, new byte[0]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RespuestaIdempotente(200, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("rechaza entrada inválida y una transacción ambiente antes de invocar efectos")
  void validaEntradaYRechazaTransaccionAmbiente() {
    AtomicBoolean callbackEjecutado = new AtomicBoolean();
    var callback =
        (java.util.function.Supplier<RespuestaIdempotente>)
            () -> {
              callbackEjecutado.set(true);
              return new RespuestaIdempotente(200, new byte[0]);
            };

    assertThatThrownBy(
            () -> servicio.ejecutar(null, "clave", huella(1, 80), Duration.ofSeconds(1), callback))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                servicio.ejecutar(usuarioId, null, huella(1, 80), Duration.ofSeconds(1), callback))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> servicio.ejecutar(usuarioId, "", huella(1, 80), Duration.ofSeconds(1), callback))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                servicio.ejecutar(
                    usuarioId, "x".repeat(101), huella(1, 80), Duration.ofSeconds(1), callback))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> servicio.ejecutar(usuarioId, "clave", null, Duration.ofSeconds(1), callback))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> servicio.ejecutar(usuarioId, "clave", huella(1, 80), null, callback))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> servicio.ejecutar(usuarioId, "clave", huella(1, 80), Duration.ZERO, callback))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                servicio.ejecutar(
                    usuarioId, "clave", huella(1, 80), Duration.ofNanos(-1), callback))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> servicio.ejecutar(usuarioId, "clave", huella(1, 80), Duration.ofSeconds(1), null))
        .isInstanceOf(IllegalArgumentException.class);

    String claveMaxima = "k".repeat(100);
    RespuestaIdempotente respuestaMinima =
        servicio.ejecutar(
            usuarioId,
            claveMaxima,
            huella(1, 82),
            Duration.ofSeconds(1),
            () -> new RespuestaIdempotente(100, new byte[0]));
    assertThat(respuestaMinima.estadoHttp()).isEqualTo(100);
    RespuestaIdempotente respuestaMaxima =
        servicio.ejecutar(
            usuarioId,
            "status-maximo",
            huella(1, 83),
            Duration.ofSeconds(1),
            () -> new RespuestaIdempotente(599, new byte[0]));
    assertThat(respuestaMaxima.estadoHttp()).isEqualTo(599);

    TransactionTemplate transaccion = new TransactionTemplate(gestorTransacciones);
    assertThatThrownBy(
            () ->
                transaccion.execute(
                    estado ->
                        servicio.ejecutar(
                            usuarioId,
                            "transaccion-ambiente",
                            huella(1, 81),
                            Duration.ofSeconds(1),
                            callback)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("transacción ambiente");
    assertThat(callbackEjecutado).isFalse();
  }

  private void assertConflicto(
      String tipo, String clave, HuellaIdempotencia huella, AtomicInteger ejecuciones) {
    assertThatThrownBy(
            () ->
                servicio.ejecutar(
                    usuarioId,
                    clave,
                    huella,
                    Duration.ofSeconds(1),
                    () -> {
                      ejecuciones.incrementAndGet();
                      return new RespuestaIdempotente(500, new byte[] {99});
                    }))
        .isInstanceOfSatisfying(
            NegocioException.class,
            error -> {
              assertThat(error.getStatusCode().value()).isEqualTo(409);
              assertThat(error.getBody().getType()).isEqualTo(URI.create(tipo));
            });
  }

  private UUID crearUsuario() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol, estado, correo_verificado_en)
        VALUES (?, ?, ?, ?, 'ESTUDIANTE', 'ACTIVO', now())
        """,
        id,
        "idempotencia-" + id + "@prueba.invalid",
        "Usuario de prueba",
        "hash");
    usuarios.add(id);
    return id;
  }

  private String nombreUsuario(UUID id) {
    return jdbc.queryForObject("SELECT nombre FROM usuario WHERE id = ?", String.class, id);
  }

  private static HuellaIdempotencia huella(int version, int semilla) {
    return new HuellaIdempotencia(version, digest(semilla));
  }

  private static byte[] digest(int semilla) {
    byte[] digest = new byte[32];
    for (int indice = 0; indice < digest.length; indice++) {
      digest[indice] = (byte) (semilla + indice);
    }
    return digest;
  }

  private static void esperar(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("se agotó el tiempo de espera de la prueba");
      }
    } catch (InterruptedException excepcion) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("prueba interrumpida", excepcion);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @EnableJpaRepositories(basePackageClasses = IdempotenciaRepository.class)
  @Import(IdempotenciaService.class)
  static class ConfiguracionDePruebas {}
}
