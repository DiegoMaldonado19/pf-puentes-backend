package com.cunoc.puentes.usuario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta del inicio de sesión. El token de renovación no va aquí: viaja en una cookie HttpOnly
 * (DT-SEC-02).
 */
public record SesionDTO(
    @JsonProperty("access_token") @Schema(description = "JWT para el encabezado Bearer")
        String tokenAcceso,
    @JsonProperty("token_type") String tipoToken,
    @JsonProperty("expires_in") @Schema(description = "Segundos de vigencia del token de acceso")
        long expiraEnSegundos,
    UsuarioSesionDTO usuario) {

  public static final String BEARER = "Bearer";

  // DT-SEC-12: el toString por defecto imprimiría el token
  @Override
  public String toString() {
    return "SesionDTO[usuario=" + usuario + "]";
  }
}
