package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.inspeccion.dto.AcompananteDTO;
import com.cunoc.puentes.inspeccion.dto.ReemplazarAcompanantesDTO;
import com.cunoc.puentes.sync.HuellaIdempotencia;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/** Identidad v1 de M04: lista ordenada, UUID canónico y nombre sin recortar. */
final class HuellaAcompanantesIdempotencia {
  private HuellaAcompanantesIdempotencia() {}

  static HuellaIdempotencia crear(
      UUID inspeccionId, ReemplazarAcompanantesDTO dto, JsonMapper json) {
    List<Map<String, Object>> filas = new ArrayList<>();
    for (AcompananteDTO fila : dto.acompanantes()) {
      Map<String, Object> campos = new TreeMap<>();
      campos.put("nombre", fila.nombre());
      campos.put("usuario_id", fila.usuarioId());
      filas.add(campos);
    }
    Map<String, Object> cuerpo = new TreeMap<>();
    cuerpo.put("acompanantes", filas);
    Map<String, Object> identidad = new TreeMap<>();
    identidad.put("method", "PUT");
    identidad.put("path", "/api/v1/inspecciones/" + inspeccionId + "/acompanantes");
    identidad.put("dto", cuerpo);
    try {
      return new HuellaIdempotencia(
          1, MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(identidad)));
    } catch (NoSuchAlgorithmException fallo) {
      throw new IllegalStateException("SHA-256 no está disponible en esta JVM.", fallo);
    }
  }
}
