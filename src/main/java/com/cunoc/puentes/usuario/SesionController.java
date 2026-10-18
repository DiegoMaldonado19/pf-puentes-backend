package com.cunoc.puentes.usuario;

import com.cunoc.puentes.usuario.dto.SesionDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints públicos: se autentican con la cookie de renovación, no con un token de acceso
 * (contrato 11). Se declaran en {@code sgp.seguridad.rutas-publicas}.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Inicio de sesión y bloqueo por intentos fallidos")
class SesionController {

  private final SesionService sesiones;
  private final CookieRenovacion cookie;

  SesionController(SesionService sesiones, CookieRenovacion cookie) {
    this.sesiones = sesiones;
    this.cookie = cookie;
  }

  @Operation(
      summary = "Renueva el token de acceso con la cookie de renovación",
      description =
          "Devuelve un token de acceso nuevo; el de renovación no rota ni va en el cuerpo."
              + " El frontend la llama una vez ante un 401 (contrato 11).")
  @ApiResponse(responseCode = "200", description = "Token de acceso nuevo")
  @ApiResponse(
      responseCode = "401",
      description =
          "Sin cookie, vencida, revocada, inexistente o de una cuenta dada de baja (sesion-invalida)."
              + " La respuesta borra la cookie.")
  @PostMapping("/renovar")
  SesionDTO renovar(
      @CookieValue(name = CookieRenovacion.NOMBRE, required = false) String tokenRenovacion) {
    return sesiones.renovar(tokenRenovacion);
  }

  @Operation(
      summary = "Cierra la sesión de este dispositivo",
      description =
          "Revoca el token de renovación de la cookie y la borra. Idempotente; no cierra las"
              + " sesiones de otros dispositivos.")
  @ApiResponse(responseCode = "204", description = "Sesión cerrada (o ya lo estaba)")
  @PostMapping("/cerrar-sesion")
  ResponseEntity<Void> cerrarSesion(
      @CookieValue(name = CookieRenovacion.NOMBRE, required = false) String tokenRenovacion) {
    sesiones.cerrar(tokenRenovacion);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie.borrar().toString())
        .build();
  }

  // El 401 uniforme además ordena borrar la cookie inservible
  @ExceptionHandler(SesionInvalidaException.class)
  ResponseEntity<ProblemDetail> sesionInvalida(SesionInvalidaException e) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .header(HttpHeaders.SET_COOKIE, cookie.borrar().toString())
        .body(e.getBody());
  }
}
