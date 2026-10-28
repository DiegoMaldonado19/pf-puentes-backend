package com.cunoc.puentes.inspeccion.puerto;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Lectura y escritura de {@code datos} JSON por rutas {@code elemento_ref} (contrato 1). */
public final class RutasDatos {

  private RutasDatos() {}

  public static Object leer(Map<String, ?> datos, String ruta) {
    if (datos == null || ruta == null || ruta.isBlank()) {
      return null;
    }
    Object actual = datos;
    for (String parte : ruta.split("\\.")) {
      if (!(actual instanceof Map<?, ?> mapa)) {
        return null;
      }
      actual = mapa.get(parte);
    }
    return actual;
  }

  @SuppressWarnings("unchecked")
  public static void escribir(Map<String, Object> datos, String ruta, Object valor) {
    if (datos == null || ruta == null || ruta.isBlank()) {
      return;
    }
    String[] partes = ruta.split("\\.");
    Map<String, Object> actual = datos;
    for (int i = 0; i < partes.length - 1; i++) {
      Object siguiente = actual.get(partes[i]);
      if (!(siguiente instanceof Map<?, ?>)) {
        Map<String, Object> creado = new LinkedHashMap<>();
        actual.put(partes[i], creado);
        actual = creado;
      } else {
        actual = (Map<String, Object>) siguiente;
      }
    }
    actual.put(partes[partes.length - 1], valor);
  }

  public static void quitar(Map<String, ?> datos, String ruta) {
    int corte = ruta.lastIndexOf('.');
    Object padre = corte < 0 ? datos : leer(datos, ruta.substring(0, corte));
    if (padre instanceof Map<?, ?> mapa) {
      mapa.remove(ruta.substring(corte + 1));
    }
  }

  public static Map<String, Object> extraer(Map<String, ?> origen, Set<String> rutas) {
    Map<String, Object> destino = new LinkedHashMap<>();
    if (origen == null || rutas == null) {
      return destino;
    }
    for (String ruta : rutas) {
      Object valor = leer(origen, ruta);
      if (valor != null) {
        escribir(destino, ruta, valor);
      }
    }
    return destino;
  }

  public static boolean tieneAlguno(Map<String, ?> datos, Set<String> rutas) {
    if (datos == null || rutas == null) {
      return false;
    }
    return rutas.stream().anyMatch(ruta -> leer(datos, ruta) != null);
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> fusionar(Map<String, ?> base, Map<String, ?> overlay) {
    Map<String, Object> destino = copiar(base);
    if (overlay == null) {
      return destino;
    }
    for (Map.Entry<String, ?> entrada : overlay.entrySet()) {
      Object actual = destino.get(entrada.getKey());
      Object nuevo = entrada.getValue();
      if (actual instanceof Map<?, ?> mapaBase && nuevo instanceof Map<?, ?> mapaNuevo) {
        destino.put(
            entrada.getKey(), fusionar((Map<String, ?>) mapaBase, (Map<String, ?>) mapaNuevo));
      } else {
        destino.put(entrada.getKey(), copiarValor(nuevo));
      }
    }
    return destino;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> copiar(Map<String, ?> origen) {
    Map<String, Object> destino = new LinkedHashMap<>();
    if (origen == null) {
      return destino;
    }
    for (Map.Entry<String, ?> entrada : origen.entrySet()) {
      destino.put(entrada.getKey(), copiarValor(entrada.getValue()));
    }
    return destino;
  }

  @SuppressWarnings("unchecked")
  private static Object copiarValor(Object valor) {
    if (valor instanceof Map<?, ?> mapa) {
      return copiar((Map<String, ?>) mapa);
    }
    return valor;
  }
}
