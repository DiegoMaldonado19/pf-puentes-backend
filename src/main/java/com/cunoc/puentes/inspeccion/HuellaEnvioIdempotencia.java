package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.inspeccion.dto.EnviarInspeccionDTO;
import com.cunoc.puentes.sync.HuellaIdempotencia;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Huella v1 del envío: DTO completo, sin los instantes generados por el servidor. */
final class HuellaEnvioIdempotencia {

  private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {};

  private HuellaEnvioIdempotencia() {}

  static HuellaIdempotencia crear(
      UUID inspeccionId, boolean sync, EnviarInspeccionDTO dto, JsonMapper json) {
    Map<String, Object> identidad = new TreeMap<>();
    identidad.put("method", "POST");
    identidad.put("path", "/api/v1/inspecciones/" + inspeccionId + "/envio");
    identidad.put("X-Sgp-Sync", sync);
    // null no es un DTO vacío: la presencia del cuerpo también integra la identidad.
    identidad.put("dto", dto == null ? null : canonizar(dto, json));
    try {
      return new HuellaIdempotencia(
          1, MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(identidad)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 no está disponible en esta JVM.", error);
    }
  }

  private static Map<String, Object> canonizar(EnviarInspeccionDTO dto, JsonMapper json) {
    // El DTO solo contiene escalares; orden de propiedades y escala decimal no son semánticos.
    Map<String, Object> campos = new TreeMap<>(json.convertValue(dto, MAPA));
    campos.replaceAll(
        (nombre, valor) -> {
          if (valor instanceof Number numero) {
            BigDecimal decimal = new BigDecimal(numero.toString());
            return decimal.signum() == 0 ? BigDecimal.ZERO : decimal.stripTrailingZeros();
          }
          return valor;
        });
    return campos;
  }
}
