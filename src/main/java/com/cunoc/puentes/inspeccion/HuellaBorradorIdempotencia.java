package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.inspeccion.dto.GuardarAvanceDTO;
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

/** Canonicaliza la huella v1 del PUT de borrador, sin cambiar la interpretación del DTO. */
final class HuellaBorradorIdempotencia {

  private static final int VERSION = 1;
  private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {};

  private HuellaBorradorIdempotencia() {}

  static HuellaIdempotencia crear(
      UUID inspeccionId, boolean sync, GuardarAvanceDTO dto, JsonMapper json) {
    Map<String, Object> identidad =
        Map.of(
            "method",
            "PUT",
            "path",
            "/api/v1/inspecciones/" + inspeccionId,
            "X-Sgp-Sync",
            sync,
            "dto",
            json.convertValue(dto, MAPA));
    byte[] canonica = json.writeValueAsBytes(ordenar(identidad));
    try {
      return new HuellaIdempotencia(VERSION, MessageDigest.getInstance("SHA-256").digest(canonica));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 no está disponible en esta JVM.", error);
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
      for (Object elemento : elementos) {
        ordenados.add(ordenar(elemento));
      }
      return ordenados;
    }
    if (valor instanceof Number numero) {
      BigDecimal decimal = new BigDecimal(numero.toString());
      return decimal.signum() == 0 ? BigDecimal.ZERO : decimal.stripTrailingZeros();
    }
    return valor;
  }
}
