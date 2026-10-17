package com.cunoc.puentes.usuario;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Token aleatorio de 256 bits para los enlaces por correo; en la base solo vive su SHA-256. */
final class TokenOpaco {

  private static final int BYTES = 32;
  private static final SecureRandom ALEATORIO = new SecureRandom();

  private TokenOpaco() {}

  /** Seguro para usar en una URL, sin relleno. */
  static String generar() {
    byte[] bytes = new byte[BYTES];
    ALEATORIO.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** 64 caracteres hexadecimales (DT-SEC-12). */
  static String hash(String token) {
    try {
      byte[] resumen =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(resumen);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 no disponible", e);
    }
  }
}
