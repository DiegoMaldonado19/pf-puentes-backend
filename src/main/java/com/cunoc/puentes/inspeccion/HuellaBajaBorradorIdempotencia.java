package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.sync.HuellaIdempotencia;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/** Identidad v1 de M08, independiente del estado mutable y de las huellas PUT/envío. */
final class HuellaBajaBorradorIdempotencia {
  private HuellaBajaBorradorIdempotencia() {}

  static HuellaIdempotencia crear(UUID inspeccionId, JsonMapper json) {
    Map<String, Object> identidad = new TreeMap<>();
    identidad.put("method", "DELETE");
    identidad.put("path", "/api/v1/inspecciones/" + inspeccionId);
    try {
      return new HuellaIdempotencia(
          1, MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(identidad)));
    } catch (NoSuchAlgorithmException fallo) {
      throw new IllegalStateException("SHA-256 no está disponible en esta JVM.", fallo);
    }
  }
}
