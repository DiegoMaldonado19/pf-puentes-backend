package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.inspeccion.dto.ConfirmarDatosPermanentesDTO;
import com.cunoc.puentes.sync.HuellaIdempotencia;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Identidad v1 de M03; no usa la huella ni la allowlist del PUT o del envío. */
final class HuellaPermanentesIdempotencia {
  private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {};

  private HuellaPermanentesIdempotencia() {}

  static HuellaIdempotencia crear(
      UUID inspeccionId, ConfirmarDatosPermanentesDTO dto, JsonMapper json) {
    Map<String, Object> identidad =
        Map.of(
            "method",
            "POST",
            "path",
            "/api/v1/inspecciones/" + inspeccionId + "/confirmacion-datos-permanentes",
            "dto",
            json.convertValue(dto, MAPA));
    try {
      return new HuellaIdempotencia(
          1,
          MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(ordenar(identidad))));
    } catch (NoSuchAlgorithmException fallo) {
      throw new IllegalStateException("SHA-256 no está disponible en esta JVM.", fallo);
    }
  }

  private static Object ordenar(Object valor) {
    if (valor instanceof Map<?, ?> mapa) {
      Map<String, Object> ordenados = new TreeMap<>();
      mapa.forEach(
          (clave, elemento) -> {
            if (!(clave instanceof String nombre)) {
              throw new IllegalArgumentException("Las propiedades JSON deben ser cadenas.");
            }
            ordenados.put(nombre, ordenar(elemento));
          });
      return ordenados;
    }
    if (valor instanceof Iterable<?> elementos) {
      List<Object> ordenados = new ArrayList<>();
      for (Object elemento : elementos) ordenados.add(ordenar(elemento));
      return ordenados;
    }
    if (valor instanceof Number numero) {
      BigDecimal decimal = new BigDecimal(numero.toString());
      return decimal.signum() == 0 ? BigDecimal.ZERO : decimal.stripTrailingZeros();
    }
    return valor;
  }
}
