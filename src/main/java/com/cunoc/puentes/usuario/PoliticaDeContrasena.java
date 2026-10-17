package com.cunoc.puentes.usuario;

/** RN-USR-06: mínimo 10 caracteres, con al menos una letra y un dígito. */
final class PoliticaDeContrasena {

  static final int LONGITUD_MINIMA = 10;
  static final String DESCRIPCION =
      "mínimo %d caracteres, con al menos una letra y un dígito".formatted(LONGITUD_MINIMA);

  private PoliticaDeContrasena() {}

  static boolean cumple(String contrasena) {
    return contrasena != null
        && contrasena.length() >= LONGITUD_MINIMA
        && contrasena.chars().anyMatch(Character::isLetter)
        && contrasena.chars().anyMatch(Character::isDigit);
  }
}
