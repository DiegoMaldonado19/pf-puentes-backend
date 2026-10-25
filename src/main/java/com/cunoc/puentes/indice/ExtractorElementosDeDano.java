package com.cunoc.puentes.indice;

import com.cunoc.puentes.common.NegocioException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Único recorrido de definiciones para referencias y rutas; no interpreta anulaciones ni agrega
 * instancias de un arreglo.
 */
final class ExtractorElementosDeDano {
  ElementosDeDano extraer(UUID version, Map<String, Object> esquema) {
    if (version == null) throw new IllegalArgumentException("La versión es obligatoria.");
    return new ElementosDeDano(version, campos(esquema).keySet());
  }

  Map<String, CampoDeDano> campos(Map<String, Object> esquema) {
    if (esquema == null || !"object".equals(esquema.get("type"))) {
      throw invalido("$", "Se requiere un esquema raíz de tipo object.");
    }
    Map<String, CampoDeDano> campos = new HashMap<>();
    recorrer(esquema, "$", List.of(), false, campos);
    return Map.copyOf(campos);
  }

  private void recorrer(
      Map<?, ?> nodo,
      String ruta,
      List<String> propiedadesRuta,
      boolean repetido,
      Map<String, CampoDeDano> campos) {
    for (String palabra :
        Set.of("$ref", "allOf", "anyOf", "oneOf", "if", "then", "else", "patternProperties")) {
      if (nodo.containsKey(palabra)) {
        throw invalido(
            ruta, "La estructura " + palabra + " requiere un contrato de extracción explícito.");
      }
    }
    if (nodo.get("additionalProperties") instanceof Map<?, ?>) {
      throw invalido(ruta, "No se admiten definiciones de daño en propiedades dinámicas.");
    }
    if (nodo.containsKey("x-dano") && !(nodo.get("x-dano") instanceof Boolean)) {
      throw invalido(ruta, "x-dano debe ser booleano.");
    }
    if (Boolean.TRUE.equals(nodo.get("x-dano"))) {
      registrar(nodo, ruta, propiedadesRuta, repetido, campos);
    }
    if (nodo.containsKey("properties")) {
      if (!"object".equals(nodo.get("type"))) {
        throw invalido(ruta, "Las propiedades deben pertenecer a un nodo object explícito.");
      }
      if (!(nodo.get("properties") instanceof Map<?, ?> propiedades)) {
        throw invalido(ruta, "properties debe ser un objeto.");
      }
      for (var propiedad : propiedades.entrySet()) {
        if (!(propiedad.getValue() instanceof Map<?, ?> hijo)) {
          throw invalido(ruta, "Cada propiedad debe describir un campo mediante un objeto.");
        }
        var siguiente = new ArrayList<>(propiedadesRuta);
        siguiente.add(propiedad.getKey().toString());
        recorrer(hijo, ruta + "." + propiedad.getKey(), siguiente, repetido, campos);
      }
    }
    if ("array".equals(nodo.get("type")) || nodo.containsKey("items")) {
      if (!"array".equals(nodo.get("type"))) {
        throw invalido(ruta, "items debe pertenecer a un nodo array explícito.");
      }
      if (!(nodo.get("items") instanceof Map<?, ?> item)) {
        throw invalido(ruta, "Los arreglos deben declarar un único esquema items.");
      }
      recorrer(item, ruta + "[]", propiedadesRuta, true, campos);
    }
  }

  private void registrar(
      Map<?, ?> nodo,
      String ruta,
      List<String> propiedadesRuta,
      boolean repetido,
      Map<String, CampoDeDano> campos) {
    if (!"integer".equals(nodo.get("type"))
        || !limite(nodo.get("minimum"), 0)
        || !limite(nodo.get("maximum"), 4)) {
      throw invalido(ruta, "El daño debe ser integer con minimum 0 y maximum 4.");
    }
    if (Boolean.TRUE.equals(nodo.get("x-permanente"))
        || nodo.containsKey("properties")
        || nodo.containsKey("items")) {
      throw invalido(ruta, "Un daño debe ser un campo individual no permanente.");
    }
    if (!(nodo.get("x-elemento-ref") instanceof String referencia)
        || referencia.isBlank()
        || referencia.codePointCount(0, referencia.length()) > 200) {
      throw invalido(ruta, "Falta una referencia de daño no vacía de hasta 200 caracteres.");
    }
    if (campos.putIfAbsent(referencia, new CampoDeDano(propiedadesRuta, repetido)) != null) {
      throw invalido(ruta, "Dos campos de daño comparten la misma referencia.");
    }
  }

  private boolean limite(Object valor, int esperado) {
    if (!(valor instanceof Number)) return false;
    try {
      return new BigDecimal(valor.toString()).compareTo(BigDecimal.valueOf(esperado)) == 0;
    } catch (NumberFormatException error) {
      return false;
    }
  }

  static NegocioException invalido(String ruta, String detalle) {
    return new NegocioException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "metadatos-de-dano-invalidos",
        "Metadatos de daño incompatibles",
        "No se pueden obtener referencias confiables en " + ruta + ". " + detalle);
  }
}
