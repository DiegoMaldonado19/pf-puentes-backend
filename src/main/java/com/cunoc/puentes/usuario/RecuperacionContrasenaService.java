package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.NegocioException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DT-SEC-07: solicitar siempre responde igual, exista o no la cuenta. Restablecer consume el token
 * de un solo uso y revoca las sesiones.
 */
@Service
public class RecuperacionContrasenaService {

  private final TokenVerificacionRepository tokens;
  private final UsuarioRepository usuarios;
  private final CambiadorDeContrasena cambiador;
  private final ControlDeIntentos intentos;
  private final SesionService sesiones;
  private final ApplicationEventPublisher publicador;
  private final Duration vigencia;

  RecuperacionContrasenaService(
      TokenVerificacionRepository tokens,
      UsuarioRepository usuarios,
      CambiadorDeContrasena cambiador,
      ControlDeIntentos intentos,
      SesionService sesiones,
      ApplicationEventPublisher publicador,
      @Value("${sgp.recuperacion-contrasena.vigencia:PT1H}") Duration vigencia) {
    this.tokens = tokens;
    this.usuarios = usuarios;
    this.cambiador = cambiador;
    this.intentos = intentos;
    this.sesiones = sesiones;
    this.publicador = publicador;
    this.vigencia = vigencia;
  }

  /**
   * Solo una cuenta {@code ACTIVO} o {@code PENDIENTE} recibe el enlace. Para cualquier otro correo
   * no hace nada y no lo dice, para no revelar qué cuentas existen.
   */
  @Transactional
  public void solicitar(String correo) {
    usuarios
        .buscarPorCorreo(correo)
        .filter(u -> u.getEstado() != EstadoUsuario.INACTIVO)
        .ifPresent(this::emitir);
  }

  @Transactional
  public void restablecer(String token, String contrasenaNueva) {
    Instant ahora = Instant.now();
    TokenVerificacion vigente =
        tokens
            .findByTokenHash(TokenOpaco.hash(token))
            .filter(t -> t.getTipo() == TipoTokenVerificacion.RECUPERACION_CONTRASENA)
            .filter(t -> t.getUsadoEn() == null && t.getExpiraEn().isAfter(ahora))
            .filter(t -> t.getUsuario().getEstado() != EstadoUsuario.INACTIVO)
            .orElseThrow(RecuperacionContrasenaService::tokenInvalido);
    if (tokens.marcarUsado(vigente.getId(), ahora) == 0) {
      throw tokenInvalido();
    }
    UUID usuarioId = vigente.getUsuario().getId();
    cambiador.cambiar(usuarioId, contrasenaNueva);
    intentos.reiniciar(usuarioId);
    sesiones.revocarTodas(usuarioId);
  }

  private void emitir(Usuario usuario) {
    Instant ahora = Instant.now();
    tokens
        .buscarVigentes(usuario.getId(), TipoTokenVerificacion.RECUPERACION_CONTRASENA, ahora)
        .forEach(anterior -> anterior.setUsadoEn(ahora));

    String token = TokenOpaco.generar();
    TokenVerificacion nuevo = new TokenVerificacion();
    nuevo.setUsuario(usuario);
    nuevo.setTipo(TipoTokenVerificacion.RECUPERACION_CONTRASENA);
    nuevo.setTokenHash(TokenOpaco.hash(token));
    nuevo.setExpiraEn(ahora.plus(vigencia));
    tokens.save(nuevo);

    publicador.publishEvent(
        new RecuperacionEmitida(
            usuario.getId(), usuario.getCorreo(), usuario.getNombre(), token, vigencia));
  }

  // Un solo mensaje para vencido, usado, de cuenta inactiva e inexistente
  private static NegocioException tokenInvalido() {
    return new NegocioException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "token-invalido",
        "El enlace de recuperación no es válido",
        "El enlace de recuperación no es válido o ya venció. Solicita uno nuevo.");
  }
}
