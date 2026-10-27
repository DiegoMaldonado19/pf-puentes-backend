package com.cunoc.puentes.archivo;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.sync.HuellaIdempotencia;
import com.cunoc.puentes.sync.IdempotenciaService;
import com.cunoc.puentes.sync.RespuestaIdempotente;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Orquesta fuera de transacción ambiente; G7.1 admite únicamente el 204 final. */
@Service
class BajaArchivoIdempotenciaService {
  private final ArchivoService archivos;
  private final ArchivoRepository repositorio;
  private final BajaArchivoIntencion intenciones;
  private final IdempotenciaService idempotencia;
  private final JdbcClient jdbc;

  BajaArchivoIdempotenciaService(
      ArchivoService archivos,
      ArchivoRepository repositorio,
      BajaArchivoIntencion intenciones,
      IdempotenciaService idempotencia,
      JdbcClient jdbc) {
    this.archivos = archivos;
    this.repositorio = repositorio;
    this.intenciones = intenciones;
    this.idempotencia = idempotencia;
    this.jdbc = jdbc;
  }

  void eliminar(UUID id, UUID usuario, String encabezado) {
    String clave = encabezado.strip();
    UUID padre = padre(id, usuario, clave);
    archivos.inspeccionPropia(padre, usuario);
    if (clave.codePointCount(0, clave.length()) > 100) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "idempotencia-invalida",
          "Clave de idempotencia inválida",
          "Idempotency-Key no puede superar 100 caracteres.");
    }
    var huella = huella(id);
    try {
      idempotencia.ejecutar(
          usuario,
          clave,
          huella,
          Duration.ofSeconds(2),
          () -> {
            jdbc.sql("set local lock_timeout = '2s'").update();
            archivos.eliminarIdempotente(id, padre, usuario, clave, huella);
            return new RespuestaIdempotente(204, new byte[0]);
          });
    } catch (RuntimeException error) {
      for (Throwable causa = error; causa != null; causa = causa.getCause()) {
        if (causa instanceof java.sql.SQLException sql && "55P03".equals(sql.getSQLState())) {
          throw new NegocioException(
              HttpStatus.CONFLICT,
              "operacion-en-proceso",
              "Operación en proceso",
              "La operación sigue en curso; se puede reintentar con la misma clave.");
        }
      }
      throw error;
    }
    archivos.inspeccionPropia(padre(id, usuario, clave), usuario);
  }

  private UUID padre(UUID id, UUID usuario, String clave) {
    // La evidencia exacta gana frente a una encarnación posterior del mismo UUID.
    return intenciones
        .buscar(usuario, clave)
        .filter(i -> i.archivoId().equals(id))
        .map(BajaArchivoIntencion.Intento::inspeccionId)
        .orElseGet(
            () ->
                repositorio
                    .findById(id)
                    .map(Archivo::getInspeccionId)
                    .orElseThrow(
                        () ->
                            new NegocioException(
                                HttpStatus.NOT_FOUND,
                                "archivo-no-encontrado",
                                "El archivo no existe",
                                "No hay un archivo con ese id.")));
  }

  private static HuellaIdempotencia huella(UUID id) {
    try {
      return new HuellaIdempotencia(
          1,
          MessageDigest.getInstance("SHA-256")
              .digest(("DELETE /api/v1/archivos/" + id).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 no está disponible en esta JVM.", error);
    }
  }
}
