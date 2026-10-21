package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.NegocioException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * US-P2-11: el titular cambia su contraseña; el id sale del token, nunca de la ruta (DT-SEC-04).
 */
@Service
public class CambioContrasenaService {

  private static final int BYTES_MAXIMOS_BCRYPT = 72;

  private final UsuarioRepository usuarios;
  private final CambiadorDeContrasena cambiador;
  private final SesionService sesiones;
  private final PasswordEncoder codificador;

  CambioContrasenaService(
      UsuarioRepository usuarios,
      CambiadorDeContrasena cambiador,
      SesionService sesiones,
      PasswordEncoder codificador) {
    this.usuarios = usuarios;
    this.cambiador = cambiador;
    this.sesiones = sesiones;
    this.codificador = codificador;
  }

  @PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO', 'ADMINISTRADOR')")
  @Transactional
  public void cambiar(UUID usuarioId, String contrasenaActual, String contrasenaNueva) {
    Usuario cuenta =
        usuarios
            .bloquear(usuarioId)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "usuario-no-encontrado",
                        "Usuario no encontrado",
                        "La cuenta ya no existe."));
    if (!coincide(contrasenaActual, cuenta.getContrasenaHash())) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "contrasena-actual-incorrecta",
          "La contraseña actual no es correcta",
          "La contraseña actual no coincide.");
    }
    if (coincide(contrasenaNueva, cuenta.getContrasenaHash())) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "contrasena-repetida",
          "La contraseña nueva debe ser distinta",
          "Elige una contraseña distinta a la actual.");
    }
    cambiador.cambiar(usuarioId, contrasenaNueva);
    sesiones.revocarTodas(usuarioId);
  }

  private boolean coincide(String contrasena, String hash) {
    String candidata =
        contrasena.getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS_BCRYPT ? "" : contrasena;
    return codificador.matches(candidata, hash);
  }
}
