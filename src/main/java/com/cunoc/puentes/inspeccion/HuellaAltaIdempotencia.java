package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.inspeccion.dto.CrearInspeccionDTO;
import com.cunoc.puentes.sync.HuellaIdempotencia;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Identidad v1 de M01: método, ruta y todos los escalares del DTO recibido. */
final class HuellaAltaIdempotencia {
  private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {};

  private HuellaAltaIdempotencia() {}

  static HuellaIdempotencia crear(CrearInspeccionDTO dto, JsonMapper json) {
    Map<String, Object> campos = new TreeMap<>(json.convertValue(dto, MAPA));
    campos.replaceAll(
        (nombre, valor) -> {
          if (valor instanceof Number numero) {
            BigDecimal decimal = new BigDecimal(numero.toString());
            return decimal.signum() == 0 ? BigDecimal.ZERO : decimal.stripTrailingZeros();
          }
          return valor;
        });
    Map<String, Object> identidad = new TreeMap<>();
    identidad.put("method", "POST");
    identidad.put("path", "/api/v1/inspecciones");
    identidad.put("dto", campos);
    try {
      return new HuellaIdempotencia(
          1, MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(identidad)));
    } catch (NoSuchAlgorithmException fallo) {
      throw new IllegalStateException("SHA-256 no está disponible en esta JVM.", fallo);
    }
  }
}
