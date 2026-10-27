package com.cunoc.puentes.archivo;

import com.cunoc.puentes.sync.HuellaIdempotencia;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

final class HuellaDocumento {
  private HuellaDocumento() {}

  static HuellaIdempotencia crear(
      UUID padre, UUID archivo, byte[] pdf, String mime, JsonMapper json) {
    Map<String, Object> parte = new TreeMap<>();
    parte.put("mime", mime);
    parte.put("sha256", sha256(pdf));
    parte.put("tamano", pdf.length);
    return new HuellaIdempotencia(
        1,
        digest(
            json.writeValueAsBytes(
                new TreeMap<>(
                    Map.of(
                        "method",
                        "POST",
                        "path",
                        "/api/v1/inspecciones/" + padre + "/documentos",
                        "id",
                        archivo.toString(),
                        "pdf",
                        parte)))));
  }

  static String sha256(byte[] contenido) {
    return HexFormat.of().formatHex(digest(contenido));
  }

  private static byte[] digest(byte[] contenido) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(contenido);
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 no está disponible en esta JVM.", error);
    }
  }
}
