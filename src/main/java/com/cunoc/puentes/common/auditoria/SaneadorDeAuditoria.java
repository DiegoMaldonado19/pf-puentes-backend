package com.cunoc.puentes.common.auditoria;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Lista de exclusión central de la bitácora (DT-SEC-12): copia los valores sin los campos sensibles
 * y con los correos enmascarados. Es la única barrera, así que ante la duda omite.
 */
final class SaneadorDeAuditoria {

  static final String ENMASCARADO = "***";

  // Cualquier clave que contenga uno de estos fragmentos se omite, a cualquier profundidad
  private static final List<String> SENSIBLES =
      List.of("contrasena", "password", "hash", "token", "secret", "secreto", "calificacion");
  private static final List<String> CAMPOS_CORREO = List.of("correo", "email");
  private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

  private SaneadorDeAuditoria() {}

  static Map<String, Object> sanear(Map<?, ?> valores) {
    Map<String, Object> limpio = new LinkedHashMap<>();
    valores.forEach(
        (clave, valor) -> {
          String nombre = String.valueOf(clave);
          if (!esSensible(nombre)) {
            limpio.put(nombre, valor(nombre, valor));
          }
        });
    return limpio;
  }

  static boolean esSensible(String clave) {
    String normalizada = clave.toLowerCase(Locale.ROOT);
    return SENSIBLES.stream().anyMatch(normalizada::contains);
  }

  static String enmascararCorreo(String correo) {
    int arroba = correo.indexOf('@');
    return arroba > 0 ? correo.charAt(0) + ENMASCARADO + correo.substring(arroba) : ENMASCARADO;
  }

  private static Object valor(String clave, Object valor) {
    return switch (valor) {
      case null -> null;
      case Map<?, ?> mapa -> sanear(mapa);
      case Collection<?> coleccion -> lista(clave, coleccion);
      case Number numero -> numero;
      case Boolean booleano -> booleano;
      case Enum<?> enumerado -> enumerado.name();
      default -> texto(clave, valor.toString());
    };
  }

  private static List<Object> lista(String clave, Collection<?> coleccion) {
    List<Object> elementos = new ArrayList<>();
    coleccion.forEach(elemento -> elementos.add(valor(clave, elemento)));
    return elementos;
  }

  private static String texto(String clave, String texto) {
    boolean campoCorreo = CAMPOS_CORREO.contains(clave.toLowerCase(Locale.ROOT));
    return campoCorreo || CORREO.matcher(texto).matches() ? enmascararCorreo(texto) : texto;
  }
}
