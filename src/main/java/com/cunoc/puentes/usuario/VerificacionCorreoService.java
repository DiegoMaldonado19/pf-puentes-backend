package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.usuario.dto.ResultadoVerificacionDTO;
import com.cunoc.puentes.usuario.dto.ResultadoVerificacionDTO.Resultado;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
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
  private final CambioDeEstadoDeCuenta cambios;
  private final ApplicationEventPublisher publicador;
  private final Duration vigencia;

  VerificacionCorreoService(
      TokenVerificacionRepository tokens,
      UsuarioRepository usuarios,
      MarcadorDeCorreoVerificado marcador,
      CambioDeEstadoDeCuenta cambios,
      ApplicationEventPublisher publicador,
      @Value("${sgp.verificacion-correo.vigencia:PT24H}") Duration vigencia) {
    this.tokens = tokens;
    this.usuarios = usuarios;
    this.marcador = marcador;
    this.cambios = cambios;
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

  /**
   * Consume el enlace: verifica el correo y activa al estudiante pendiente (RN-USR-01, RN-USR-02).
   * Un enlace ya usado de una cuenta activa no es un error: responde YA_ACTIVA.
   */
  @Transactional
  public ResultadoVerificacionDTO verificar(String token) {
    Instant ahora = Instant.now();
    TokenVerificacion enlace =
        tokens
            .findByTokenHash(TokenOpaco.hash(token))
            .filter(t -> t.getTipo() == TipoTokenVerificacion.VERIFICACION_CORREO)
            .orElseThrow(VerificacionCorreoService::tokenInvalido);
    if (enlace.getUsadoEn() != null || !enlace.getExpiraEn().isAfter(ahora)) {
      return yaActivaSiCorresponde(enlace.getUsuario());
    }
    // Atómico: dos peticiones con el mismo enlace no pueden consumirlo las dos
    if (tokens.marcarUsado(enlace.getId(), ahora) == 0) {
      return yaActivaSiCorresponde(enlace.getUsuario());
    }
    return verificarYActivar(enlace.getUsuario().getId());
  }

  private ResultadoVerificacionDTO verificarYActivar(UUID usuarioId) {
    marcador.marcar(usuarioId);
    Usuario cuenta =
        usuarios.findById(usuarioId).orElseThrow(VerificacionCorreoService::tokenInvalido);
    // Una cuenta dada de baja no vuelve con un enlace: lo decide un administrador
    if (cuenta.getRol() != Rol.ESTUDIANTE || cuenta.getEstado() == EstadoUsuario.INACTIVO) {
      throw tokenInvalido();
    }
    if (cuenta.getEstado() == EstadoUsuario.PENDIENTE) {
      cambios.activar(usuarioId);
      return new ResultadoVerificacionDTO(Resultado.ACTIVADA);
    }
    return new ResultadoVerificacionDTO(Resultado.YA_ACTIVA);
  }

  private static ResultadoVerificacionDTO yaActivaSiCorresponde(Usuario cuenta) {
    if (cuenta.getEstado() != EstadoUsuario.ACTIVO || cuenta.getCorreoVerificadoEn() == null) {
      throw tokenInvalido();
    }
    return new ResultadoVerificacionDTO(Resultado.YA_ACTIVA);
  }

  // Un solo mensaje para vencido, usado e inexistente: no se distingue por qué falló
  private static NegocioException tokenInvalido() {
    return new NegocioException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "token-invalido",
        "El enlace de verificación no es válido",
        "El enlace de verificación no es válido o ya venció. Solicita uno nuevo.");
  }
}
