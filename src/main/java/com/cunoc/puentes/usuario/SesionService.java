package com.cunoc.puentes.usuario;

import com.cunoc.puentes.usuario.dto.SesionDTO;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Renovación y cierre de sesión (DT-SEC-01). El token de renovación no rota: el mismo sirve hasta
 * que vence o se revoca (ADR 0017). Renovar no se audita: no cambia datos de negocio.
 */
@Service
public class SesionService {

  private final TokenRenovacionRepository renovaciones;
  private final EmisorDeAcceso emisorDeAcceso;

  SesionService(TokenRenovacionRepository renovaciones, EmisorDeAcceso emisorDeAcceso) {
    this.renovaciones = renovaciones;
    this.emisorDeAcceso = emisorDeAcceso;
  }

  /**
   * Un token de acceso nuevo a cambio de un token de renovación vigente. Comprueba la cuenta en
   * cada llamada: una baja corta la renovación al instante (RN-USR-08).
   */
  SesionDTO renovar(String tokenRenovacion) {
    if (!StringUtils.hasText(tokenRenovacion)) {
      throw new SesionInvalidaException();
    }
    Usuario cuenta =
        renovaciones
            .buscarVigente(TokenOpaco.hash(tokenRenovacion), Instant.now())
            .map(TokenRenovacion::getUsuario)
            .filter(usuario -> usuario.getEstado() == EstadoUsuario.ACTIVO)
            .orElseThrow(SesionInvalidaException::new);
    return emisorDeAcceso.emitir(cuenta);
  }

  /** Revoca solo esta sesión. Idempotente: un token ya revocado o desconocido no es un error. */
  @Transactional
  void cerrar(String tokenRenovacion) {
    if (StringUtils.hasText(tokenRenovacion)) {
      renovaciones.revocar(TokenOpaco.hash(tokenRenovacion), Instant.now());
    }
  }

  /**
   * Revoca todas las sesiones vigentes de la cuenta (baja, cambio de contraseña): ninguna podrá
   * renovar. Operación interna para US-P2-11 y US-P2-12.
   *
   * @return cuántas sesiones quedaron revocadas
   */
  @Transactional
  public int revocarTodas(UUID usuarioId) {
    return renovaciones.revocarTodas(usuarioId, Instant.now());
  }
}
