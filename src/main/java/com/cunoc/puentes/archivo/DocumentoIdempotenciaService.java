package com.cunoc.puentes.archivo;

import com.cunoc.puentes.archivo.dto.SubirDocumentoDTO;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.sync.IdempotenciaService;
import com.cunoc.puentes.sync.RespuestaIdempotente;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Orquestador sin transacción ambiente: G7.1 conserva la autoridad sobre resultado/clave. */
@Service
class DocumentoIdempotenciaService {
  private final ArchivoService archivos;
  private final IdempotenciaService idempotencia;
  private final JdbcClient jdbc;
  private final JsonMapper json;

  DocumentoIdempotenciaService(
      ArchivoService archivos, IdempotenciaService idempotencia, JdbcClient jdbc, JsonMapper json) {
    this.archivos = archivos;
    this.idempotencia = idempotencia;
    this.jdbc = jdbc;
    this.json = json;
  }

  RespuestaIdempotente guardar(UUID padre, UUID autor, SubirDocumentoDTO datos, String encabezado) {
    archivos.inspeccionPropia(padre, autor);
    String clave = encabezado.strip();
    if (clave.codePointCount(0, clave.length()) > 100) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "idempotencia-invalida",
          "Clave de idempotencia inválida",
          "Idempotency-Key no puede superar 100 caracteres.");
    }
    var huella =
        HuellaDocumento.crear(
            padre,
            datos.id(),
            ArchivoService.bytes(datos.pdf()),
            datos.pdf().getContentType(),
            json);
    RespuestaIdempotente resultado;
    try {
      resultado =
          idempotencia.ejecutar(
              autor,
              clave,
              huella,
              Duration.ofSeconds(2),
              () -> {
                jdbc.sql("set local lock_timeout = '2s'").update();
                return new RespuestaIdempotente(
                    201,
                    json.writeValueAsBytes(
                        archivos.guardarDocumento(padre, autor, datos, clave, huella)));
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
    // Incluye el camino replay, cuyo callback no se ejecuta, y la espera del advisory lock.
    archivos.inspeccionPropia(padre, autor);
    return resultado;
  }
}
