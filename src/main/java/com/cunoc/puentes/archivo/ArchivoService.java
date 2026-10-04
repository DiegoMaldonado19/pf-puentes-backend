package com.cunoc.puentes.archivo;

import com.cunoc.puentes.common.NegocioException;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ArchivoService {

  static final long MAX_BYTES_PDF = 20L * 1024 * 1024; // RN-ARC-04

  private final AlmacenamientoArchivos almacenamiento;

  public ArchivoService(AlmacenamientoArchivos almacenamiento) {
    this.almacenamiento = almacenamiento;
  }

  /** DT-ALM-04: la foto comprimida y su miniatura de 300 px llegan ya generadas por el cliente. */
  public FotoGuardada guardarFoto(UUID puenteId, UUID inspeccionId, byte[] foto, byte[] miniatura) {
    TipoArchivo tipoFoto = tipoDeFoto(foto);
    TipoArchivo tipoMiniatura = tipoDeFoto(miniatura);
    String nombre = carpeta(puenteId, inspeccionId) + UUID.randomUUID();
    return new FotoGuardada(
        guardar(nombre, foto, tipoFoto), guardar(nombre + "-miniatura", miniatura, tipoMiniatura));
  }

  public String guardarDocumento(UUID puenteId, UUID inspeccionId, byte[] pdf) {
    if (TipoArchivo.detectar(pdf).filter(TipoArchivo.PDF::equals).isEmpty()) {
      throw invalido("El documento debe ser PDF.");
    }
    if (pdf.length > MAX_BYTES_PDF) {
      throw invalido("El PDF pasa de 20 MB.");
    }
    return guardar(carpeta(puenteId, inspeccionId) + UUID.randomUUID(), pdf, TipoArchivo.PDF);
  }

  private String guardar(String nombre, byte[] contenido, TipoArchivo tipo) {
    String clave = nombre + "." + tipo.extension;
    almacenamiento.guardar(clave, contenido, tipo.mime);
    return clave;
  }

  // DT-ALM-02 + DT-SEC-08: {año}/{mes}/{puente_id}/{inspeccion_id}/, nunca el nombre del cliente
  private static String carpeta(UUID puenteId, UUID inspeccionId) {
    YearMonth mes = YearMonth.now(ZoneOffset.UTC);
    return "%d/%02d/%s/%s/".formatted(mes.getYear(), mes.getMonthValue(), puenteId, inspeccionId);
  }

  private static TipoArchivo tipoDeFoto(byte[] contenido) {
    return TipoArchivo.detectar(contenido)
        .filter(TipoArchivo::esFoto)
        .orElseThrow(() -> invalido("La foto debe ser WebP o JPEG."));
  }

  private static NegocioException invalido(String detalle) {
    return new NegocioException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "archivo-invalido",
        "El archivo no se puede guardar",
        detalle);
  }

  public record FotoGuardada(String clave, String claveMiniatura) {}
}
