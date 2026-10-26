package com.cunoc.puentes.sync;

import com.cunoc.puentes.common.NegocioException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Núcleo de reserva, ejecución y replay sin conocimiento de handlers ni DTO de operaciones. */
@Service
public class IdempotenciaService {

  private static final int LONGITUD_MAXIMA_CLAVE = 100;
  private static final long PAUSA_REINTENTO_NS = Duration.ofMillis(10).toNanos();
  private static final String BLOQUEO_ADVISORY_SQL = "SELECT pg_try_advisory_xact_lock(?)";

  private final TransactionTemplate transacciones;
  private final JdbcTemplate jdbc;
  private final IdempotenciaRepository repositorio;

  public IdempotenciaService(
      PlatformTransactionManager gestor, JdbcTemplate jdbc, IdempotenciaRepository repositorio) {
    this.transacciones = new TransactionTemplate(gestor);
    this.transacciones.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    this.transacciones.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.jdbc = jdbc;
    this.repositorio = repositorio;
  }

  /**
   * Ejecuta el callback una sola vez por usuario/clave, dentro de la misma transacción PostgreSQL
   * que guarda el resultado. El callback solo admite una respuesta si devuelve un valor no nulo.
   * Cada llamada debe aportar su propio límite de espera; nunca se fija un límite implícito.
   */
  public RespuestaIdempotente ejecutar(
      UUID usuarioId,
      String clave,
      HuellaIdempotencia huella,
      Duration esperaMaxima,
      Supplier<RespuestaIdempotente> operacion) {
    validar(usuarioId, clave, huella, esperaMaxima, operacion);
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "El núcleo de idempotencia no admite una transacción ambiente.");
    }

    long claveBloqueo = claveBloqueo(usuarioId, clave);
    try {
      return transacciones.execute(
          estado ->
              ejecutarEnTransaccion(
                  usuarioId, clave, huella, esperaMaxima, operacion, claveBloqueo));
    } catch (EsperaAgotadaException excepcion) {
      throw operacionEnProceso();
    }
  }

  private RespuestaIdempotente ejecutarEnTransaccion(
      UUID usuarioId,
      String clave,
      HuellaIdempotencia huella,
      Duration esperaMaxima,
      Supplier<RespuestaIdempotente> operacion,
      long claveBloqueo) {
    adquirirBloqueo(claveBloqueo, esperaMaxima);

    Optional<IdempotenciaRepository.ResultadoGuardado> existente =
        repositorio.buscarResultado(usuarioId, clave);
    if (existente.isPresent()) {
      return reproducir(existente.get(), huella);
    }

    RespuestaIdempotente resultado = operacion.get();
    if (resultado == null) {
      throw new IllegalStateException("El callback debe devolver una respuesta admitida.");
    }

    Idempotencia reserva = new Idempotencia();
    reserva.setUsuarioId(usuarioId);
    reserva.setClave(clave);
    reserva.setHuellaVersion(huella.version());
    reserva.setHuellaSha256(huella.sha256());
    reserva.setEstadoHttp(resultado.estadoHttp());
    reserva.setRespuesta(null);
    reserva.setRespuestaBytes(resultado.cuerpo());
    repositorio.save(reserva);
    return resultado;
  }

  private RespuestaIdempotente reproducir(
      IdempotenciaRepository.ResultadoGuardado existente, HuellaIdempotencia recibida) {
    Integer versionGuardada = existente.getHuellaVersion();
    byte[] huellaGuardada = existente.getHuellaSha256();
    byte[] bytesGuardados = existente.getRespuestaBytes();
    if (versionGuardada == null || huellaGuardada == null || bytesGuardados == null) {
      // Las filas pre-G7.1 solo conservan JSONB; no es posible reconstruir bytes ni huella exacta.
      throw claveIncompatible();
    }
    if (versionGuardada != recibida.version()
        || !MessageDigest.isEqual(huellaGuardada, recibida.sha256())) {
      throw claveIncompatible();
    }
    return new RespuestaIdempotente(existente.getEstadoHttp(), bytesGuardados);
  }

  private void adquirirBloqueo(long claveBloqueo, Duration esperaMaxima) {
    long limiteNs = nanosSaturados(esperaMaxima);
    long inicio = System.nanoTime();

    while (true) {
      if (Thread.currentThread().isInterrupted()) {
        throw new IllegalStateException("La espera de idempotencia fue interrumpida.");
      }
      long transcurrido = System.nanoTime() - inicio;
      if (transcurrido >= limiteNs) {
        throw new EsperaAgotadaException();
      }

      Boolean adquirido = jdbc.queryForObject(BLOQUEO_ADVISORY_SQL, Boolean.class, claveBloqueo);
      transcurrido = System.nanoTime() - inicio;
      if (Boolean.TRUE.equals(adquirido)) {
        if (transcurrido >= limiteNs) {
          throw new EsperaAgotadaException();
        }
        return;
      }

      long restante = limiteNs - transcurrido;
      if (restante <= 0) {
        throw new EsperaAgotadaException();
      }
      LockSupport.parkNanos(Math.min(restante, PAUSA_REINTENTO_NS));
    }
  }

  private static long claveBloqueo(UUID usuarioId, String clave) {
    byte[] bytesClave = clave.getBytes(StandardCharsets.UTF_8);
    ByteBuffer identidad =
        ByteBuffer.allocate(Long.BYTES * 2 + Integer.BYTES + bytesClave.length)
            .putLong(usuarioId.getMostSignificantBits())
            .putLong(usuarioId.getLeastSignificantBits())
            .putInt(bytesClave.length)
            .put(bytesClave);
    try {
      return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(identidad.array()))
          .getLong();
    } catch (NoSuchAlgorithmException excepcion) {
      throw new IllegalStateException("SHA-256 no está disponible en esta JVM.", excepcion);
    }
  }

  private static long nanosSaturados(Duration duracion) {
    try {
      return duracion.toNanos();
    } catch (ArithmeticException desbordamiento) {
      return Long.MAX_VALUE;
    }
  }

  private static void validar(
      UUID usuarioId,
      String clave,
      HuellaIdempotencia huella,
      Duration esperaMaxima,
      Supplier<RespuestaIdempotente> operacion) {
    if (usuarioId == null) {
      throw new IllegalArgumentException("usuarioId es obligatorio.");
    }
    if (clave == null
        || clave.isEmpty()
        || clave.codePointCount(0, clave.length()) > LONGITUD_MAXIMA_CLAVE) {
      throw new IllegalArgumentException("La clave debe contener entre 1 y 100 caracteres.");
    }
    if (huella == null) {
      throw new IllegalArgumentException("La huella es obligatoria.");
    }
    if (esperaMaxima == null || esperaMaxima.isZero() || esperaMaxima.isNegative()) {
      throw new IllegalArgumentException(
          "La espera máxima debe ser una duración positiva explícita.");
    }
    if (operacion == null) {
      throw new IllegalArgumentException("El callback es obligatorio.");
    }
  }

  private static NegocioException claveIncompatible() {
    return new NegocioException(
        HttpStatus.CONFLICT,
        "clave-idempotencia-incompatible",
        "Clave de idempotencia incompatible",
        "La clave ya está asociada a otra huella o a un resultado heredado no verificable.");
  }

  private static NegocioException operacionEnProceso() {
    return new NegocioException(
        HttpStatus.CONFLICT,
        "operacion-en-proceso",
        "Operación en proceso",
        "La operación sigue en curso; se puede reintentar con la misma clave.");
  }

  private static final class EsperaAgotadaException extends RuntimeException {}
}
