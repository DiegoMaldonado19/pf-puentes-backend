package com.cunoc.puentes.usuario.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Qué pasó al consumir el enlace de verificación del correo (RN-USR-01). */
public record ResultadoVerificacionDTO(
    @Schema(
            description =
                "ACTIVADA: el enlace verificó el correo y activó la cuenta. "
                    + "YA_ACTIVA: la cuenta ya estaba activa.")
        Resultado resultado) {

  public enum Resultado {
    ACTIVADA,
    YA_ACTIVA
  }
}
