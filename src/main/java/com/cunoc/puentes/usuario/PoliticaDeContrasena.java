package com.cunoc.puentes.usuario;

import java.nio.charset.StandardCharsets;

/**
 * RN-USR-06: mínimo 10 caracteres, con al menos una letra y un dígito. BCrypt solo usa los primeros
 * 72 bytes, así que más largo se rechaza en lugar de truncarse en silencio.
 */
public final class PoliticaDeContrasena {

  static final int LONGITUD_MINIMA = 10;
  static final int BYTES_MAXIMOS = 72;
  public static final String DESCRIPCION =
      "mínimo %d caracteres (máximo %d bytes), con al menos una letra y un dígito"
          .formatted(LONGITUD_MINIMA, BYTES_MAXIMOS);

  private PoliticaDeContrasena() {}

  public static boolean cumple(String contrasena) {
    return contrasena != null
        && contrasena.length() >= LONGITUD_MINIMA
        && contrasena.getBytes(StandardCharsets.UTF_8).length <= BYTES_MAXIMOS
        && contrasena.chars().anyMatch(Character::isLetter)
        && contrasena.chars().anyMatch(Character::isDigit);
  }
}
