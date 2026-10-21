package com.cunoc.puentes.usuario;

import com.cunoc.puentes.usuario.dto.CambiarContrasenaDTO;
import com.cunoc.puentes.usuario.dto.PerfilDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios")
@PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO', 'ADMINISTRADOR')")
@Tag(name = "Usuarios", description = "Cuentas de usuario")
@ApiResponse(responseCode = "401", description = "Sin sesión o con el token vencido")
class UsuarioController {

  private final PerfilService perfiles;
  private final CambioContrasenaService contrasenas;
  private final CookieRenovacion cookie;

  UsuarioController(
      PerfilService perfiles, CambioContrasenaService contrasenas, CookieRenovacion cookie) {
    this.perfiles = perfiles;
    this.contrasenas = contrasenas;
    this.cookie = cookie;
  }

  @Operation(summary = "Consulta los datos de la cuenta autenticada")
  @ApiResponse(responseCode = "200", description = "Id, nombre, correo, rol y estado")
  @GetMapping("/me")
  PerfilDTO perfilPropio(Principal usuario) {
    // Contrato 14: el nombre de la autenticación es el UUID del usuario
    return perfiles.obtener(UUID.fromString(usuario.getName()));
  }

  @Operation(
      summary = "Cambia la contraseña de la cuenta autenticada",
      description =
          "El id sale del token, nunca de la ruta (DT-SEC-04). Revoca las sesiones y borra la"
              + " cookie: hay que volver a iniciar sesión.")
  @ApiResponse(
      responseCode = "204",
      description = "Contraseña actualizada; la cookie queda borrada")
  @ApiResponse(
      responseCode = "400",
      description = "Falta un campo o la nueva no cumple la política")
  @ApiResponse(
      responseCode = "422",
      description =
          "La actual no coincide o la nueva es igual (contrasena-actual-incorrecta,"
              + " contrasena-repetida)")
  @PutMapping("/me/contrasena")
  ResponseEntity<Void> cambiarContrasena(
      Principal usuario, @Valid @RequestBody CambiarContrasenaDTO datos) {
    contrasenas.cambiar(
        UUID.fromString(usuario.getName()), datos.contrasenaActual(), datos.contrasenaNueva());
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie.borrar().toString())
        .build();
  }
}
