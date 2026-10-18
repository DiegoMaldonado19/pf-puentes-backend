package com.cunoc.puentes.usuario;

import com.cunoc.puentes.usuario.AutenticacionService.SesionIniciada;
import com.cunoc.puentes.usuario.dto.LoginDTO;
import com.cunoc.puentes.usuario.dto.SesionDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint público (RN-USR-10): se declara en {@code sgp.seguridad.rutas-publicas}. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Inicio de sesión y bloqueo por intentos fallidos")
class AutenticacionController {

  private final AutenticacionService autenticacion;
  private final CookieRenovacion cookie;

  AutenticacionController(AutenticacionService autenticacion, CookieRenovacion cookie) {
    this.autenticacion = autenticacion;
    this.cookie = cookie;
  }

  @Operation(
      summary = "Inicia sesión con correo y contraseña",
      description =
          "Devuelve el token de acceso (15 minutos) y deja el de renovación (7 días) en una cookie"
              + " HttpOnly. 5 fallos en 15 minutos bloquean la cuenta 15 minutos (RN-USR-07).")
  @ApiResponse(responseCode = "200", description = "Sesión iniciada; trae la cookie de renovación")
  @ApiResponse(responseCode = "400", description = "Falta el correo o la contraseña")
  @ApiResponse(
      responseCode = "401",
      description = "Correo o contraseña incorrectos (credenciales-invalidas); no dice cuál")
  @ApiResponse(
      responseCode = "403",
      description =
          "Con la contraseña correcta, la cuenta está bloqueada, pendiente o inactiva"
              + " (cuenta-bloqueada, cuenta-pendiente, cuenta-inactiva)")
  @PostMapping("/login")
  ResponseEntity<SesionDTO> iniciarSesion(@Valid @RequestBody LoginDTO datos) {
    SesionIniciada iniciada = autenticacion.iniciarSesion(datos.correo(), datos.contrasena());
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            cookie.crear(iniciada.tokenRenovacion(), iniciada.vigenciaRenovacion()).toString())
        .body(iniciada.sesion());
  }
}
