package com.cunoc.puentes.usuario;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * La cookie del token de renovación (DT-SEC-02): HttpOnly, SameSite=Strict y acotada a {@code
 * /api/v1/auth}. {@code Secure} solo se relaja en desarrollo local, por configuración.
 */
@Component
class CookieRenovacion {

  static final String NOMBRE = "refresh_token";
  static final String RUTA = "/api/v1/auth";

  private final boolean segura;

  CookieRenovacion(@Value("${sgp.renovacion.cookie-segura:true}") boolean segura) {
    this.segura = segura;
  }

  ResponseCookie crear(String token, Duration vigencia) {
    return ResponseCookie.from(NOMBRE, token)
        .httpOnly(true)
        .secure(segura)
        .sameSite("Strict")
        .path(RUTA)
        .maxAge(vigencia)
        .build();
  }
}
