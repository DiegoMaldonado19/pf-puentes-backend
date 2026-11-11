package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.NegocioException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** RN-USR-01: emite, reenvía y consume los enlaces que prueban que el correo es del estudiante. */
@Service
public class VerificacionCorreoService {

  private final TokenVerificacionRepository tokens;
  private final UsuarioRepository usuarios;
  private final MarcadorDeCorreoVerificado marcador;
  private final ApplicationEventPublisher publicador;
  private final Duration vigencia;

  VerificacionCorreoService(
      TokenVerificacionRepository tokens,
      UsuarioRepository usuarios,
      MarcadorDeCorreoVerificado marcador,
      ApplicationEventPublisher publicador,
      @Value("${sgp.verificacion-correo.vigencia:PT24H}") Duration vigencia) {
    this.tokens = tokens;
    this.usuarios = usuarios;
    this.marcador = marcador;
    this.publicador = publicador;
    this.vigencia = vigencia;
  }

  /**
   * Anula los enlaces anteriores de la cuenta, guarda el hash del nuevo y pide enviarlo cuando la
   * transacción en curso se confirme.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  void emitir(Usuario usuario) {
    Instant ahora = Instant.now();
    tokens
        .buscarVigentes(usuario.getId(), TipoTokenVerificacion.VERIFICACION_CORREO, ahora)
        .forEach(anterior -> anterior.setUsadoEn(ahora));

    String token = TokenOpaco.generar();
    TokenVerificacion nuevo = new TokenVerificacion();
    nuevo.setUsuario(usuario);
    nuevo.setTipo(TipoTokenVerificacion.VERIFICACION_CORREO);
    nuevo.setTokenHash(TokenOpaco.hash(token));
    nuevo.setExpiraEn(ahora.plus(vigencia));
    tokens.save(nuevo);

    publicador.publishEvent(
        new VerificacionEmitida(
            usuario.getId(), usuario.getCorreo(), usuario.getNombre(), token, vigencia));
  }

  /**
   * Solo una cuenta pendiente con el correo sin verificar recibe un enlace nuevo. Para cualquier
   * otro correo no hace nada y no lo dice, para no revelar qué cuentas existen.
   */
  @Transactional
  public void reenviar(String correo) {
    usuarios
        .buscarPorCorreo(correo)
        .filter(u -> u.getEstado() == EstadoUsuario.PENDIENTE && u.getCorreoVerificadoEn() == null)
        .ifPresent(this::emitir);
  }

  /** Marca el correo como verificado y activa la cuenta pendiente para permitir el inicio de sesión. */
  @Transactional
  public void verificar(String token) {
    Instant ahora = Instant.now();
    String hash = TokenOpaco.hash(token);
    TokenVerificacion vigente =
        tokens
            .findByTokenHash(hash)
            .filter(t -> t.getTipo() == TipoTokenVerificacion.VERIFICACION_CORREO)
            .filter(t -> t.getUsadoEn() == null && t.getExpiraEn().isAfter(ahora))
            .orElse(null);
    if (vigente == null) {
      // Idempotente: reintentos o clics repetidos no fallan si el correo ya quedó verificado
      boolean yaVerificado =
          tokens
              .findByTokenHash(hash)
              .filter(t -> t.getTipo() == TipoTokenVerificacion.VERIFICACION_CORREO)
              .filter(t -> t.getUsadoEn() != null)
              .map(TokenVerificacion::getUsuario)
              .filter(u -> u.getCorreoVerificadoEn() != null)
              .isPresent();
      if (yaVerificado) {
        return;
      }
      throw tokenInvalido();
    }
    // Atómico: dos peticiones con el mismo enlace no pueden consumirlo las dos
    if (tokens.marcarUsado(vigente.getId(), ahora) == 0) {
      boolean yaVerificado =
          vigente.getUsuario() != null && vigente.getUsuario().getCorreoVerificadoEn() != null;
      if (yaVerificado) {
        return;
      }
      throw tokenInvalido();
    }
    marcador.marcar(vigente.getUsuario().getId());
  }

  // Un solo mensaje para vencido, usado e inexistente: no se distingue por qué falló
  private static NegocioException tokenInvalido() {
    return new NegocioException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "token-invalido",
        "El enlace de verificación no es válido",
        "El enlace de verificación no es válido o ya venció. Solicita uno nuevo.");
  }
}
