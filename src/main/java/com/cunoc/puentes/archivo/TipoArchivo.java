package com.cunoc.puentes.archivo;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/** Formatos aceptados (RN-ARC-02, RN-ARC-04), reconocidos por su firma binaria (DT-SEC-08). */
public enum TipoArchivo {
  JPEG("image/jpeg", "jpg"),
  WEBP("image/webp", "webp"),
  PDF("application/pdf", "pdf");

  private static final byte[] INICIO_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
  private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] MARCA_WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] INICIO_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

  final String mime;
  final String extension;

  TipoArchivo(String mime, String extension) {
    this.mime = mime;
    this.extension = extension;
  }

  static Optional<TipoArchivo> detectar(byte[] contenido) {
    if (tieneEn(contenido, 0, INICIO_JPEG)) {
      return Optional.of(JPEG);
    }
    if (tieneEn(contenido, 0, RIFF) && tieneEn(contenido, 8, MARCA_WEBP)) {
      return Optional.of(WEBP);
    }
    if (tieneEn(contenido, 0, INICIO_PDF)) {
      return Optional.of(PDF);
    }
    return Optional.empty();
  }

  private static boolean tieneEn(byte[] contenido, int desde, byte[] firma) {
    int hasta = desde + firma.length;
    return contenido.length >= hasta
        && Arrays.equals(contenido, desde, hasta, firma, 0, firma.length);
  }
}
