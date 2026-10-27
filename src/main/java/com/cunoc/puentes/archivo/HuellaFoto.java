package com.cunoc.puentes.archivo;

import com.cunoc.puentes.archivo.dto.SubirFotoDTO;
import com.cunoc.puentes.sync.HuellaIdempotencia;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

final class HuellaFoto {
  private HuellaFoto() {}

  static HuellaIdempotencia crear(UUID padre, SubirFotoDTO datos, JsonMapper json) {
    Map<String, Object> contenido = new TreeMap<>();
    contenido.put("method", "POST");
    contenido.put("path", "/api/v1/inspecciones/" + padre + "/fotos");
    contenido.put("id", datos.id().toString());
    contenido.put("foto", parte(datos.foto()));
    contenido.put("miniatura", parte(datos.miniatura()));
    contenido.put("elemento_ref", datos.elementoRef());
    contenido.put("latitud", numero(datos.latitud()));
    contenido.put("longitud", numero(datos.longitud()));
    contenido.put("capturada_en", datos.capturadaEn().toString());
    try {
      return new HuellaIdempotencia(
          1, MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(contenido)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 no está disponible en esta JVM.", e);
    }
  }

  private static Object numero(Double valor) {
    return valor == null ? null : java.math.BigDecimal.valueOf(valor).stripTrailingZeros();
  }

  private static Map<String, Object> parte(MultipartFile archivo) {
    byte[] bytes = ArchivoService.bytes(archivo);
    Map<String, Object> parte = new TreeMap<>();
    parte.put("mime", archivo.getContentType());
    parte.put("sha256", HuellaDocumento.sha256(bytes));
    parte.put("tamano", bytes.length);
    return parte;
  }
}
