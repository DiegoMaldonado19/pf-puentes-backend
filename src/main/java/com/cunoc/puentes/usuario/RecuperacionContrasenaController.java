package com.cunoc.puentes.usuario;

import com.cunoc.puentes.usuario.dto.RecuperarContrasenaDTO;
import com.cunoc.puentes.usuario.dto.RestablecerContrasenaDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints públicos de recuperación (DT-SEC-07): se declaran en {@code
 * sgp.seguridad.rutas-publicas}. El token viaja en el cuerpo para no quedar en bitácoras de acceso.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Recuperación de contraseña", description = "Solicitar y restablecer sin sesión")
@ApiResponse(responseCode = "400", description = "Falta un campo o su formato no es válido")
class RecuperacionContrasenaController {

  private final RecuperacionContrasenaService recuperacion;
  private final CookieRenovacion cookie;

  RecuperacionContrasenaController(
      RecuperacionContrasenaService recuperacion, CookieRenovacion cookie) {
    this.recuperacion = recuperacion;
    this.cookie = cookie;
  }

  @Operation(
      summary = "Solicita el enlace de recuperación",
      description = "Responde igual exista o no la cuenta, para no revelarlo.")
  @ApiResponse(responseCode = "202", description = "Solicitud recibida")
  @PostMapping("/recuperar-contrasena")
  @ResponseStatus(HttpStatus.ACCEPTED)
  void solicitar(@Valid @RequestBody RecuperarContrasenaDTO datos) {
    recuperacion.solicitar(datos.correo());
  }

  @Operation(
      summary = "Restablece la contraseña con el token del enlace",
      description = "Revoca las sesiones y reinicia el bloqueo por intentos.")
  @ApiResponse(
      responseCode = "204",
      description = "Contraseña actualizada; la cookie queda borrada")
  @ApiResponse(
      responseCode = "422",
      description = "El token venció, ya se usó o no existe (token-invalido)")
  @PostMapping("/restablecer-contrasena")
  ResponseEntity<Void> restablecer(@Valid @RequestBody RestablecerContrasenaDTO datos) {
    recuperacion.restablecer(datos.token(), datos.contrasena());
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie.borrar().toString())
        .build();
  }
}
