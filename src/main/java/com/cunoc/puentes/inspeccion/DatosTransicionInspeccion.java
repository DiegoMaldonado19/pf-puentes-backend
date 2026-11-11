package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.indice.Anulacion;
import com.cunoc.puentes.indice.EstadoCondicion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Entrada interna de la máquina. El IC solo viaja cuando el destino es PUBLICADA. */
public record DatosTransicionInspeccion(
    Instant ocurridaEn, String motivo, UUID revisionId, Publicacion publicacion) {

  /** La columna motivo guarda 500 caracteres: una observación más larga se recorta, no falla. */
  public static final int MAXIMO_MOTIVO = 500;

  public static DatosTransicionInspeccion de(String motivo, UUID revisionId) {
    return new DatosTransicionInspeccion(Instant.now(), recortar(motivo), revisionId, null);
  }

  static String recortar(String motivo) {
    if (motivo == null || motivo.codePointCount(0, motivo.length()) <= MAXIMO_MOTIVO) {
      return motivo;
    }
    return motivo.substring(0, motivo.offsetByCodePoints(0, MAXIMO_MOTIVO));
  }

  public static DatosTransicionInspeccion en(Instant ocurridaEn) {
    return new DatosTransicionInspeccion(
        ocurridaEn == null ? Instant.now() : ocurridaEn, null, null, null);
  }

  public static DatosTransicionInspeccion publicacion(UUID revisionId, Publicacion ic) {
    return new DatosTransicionInspeccion(Instant.now(), null, revisionId, ic);
  }

  public record Publicacion(
      BigDecimal indiceCondicion,
      EstadoCondicion estadoCalculado,
      EstadoCondicion estadoConfirmado,
      UUID versionPesosId,
      Anulacion anulacion,
      UUID estadoConfirmadoPor,
      String justificacionEstado) {}
}
