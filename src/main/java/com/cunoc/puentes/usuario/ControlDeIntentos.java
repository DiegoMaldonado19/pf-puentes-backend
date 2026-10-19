package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RN-USR-07: el conteo de intentos fallidos vive en la base (DT-ARQ-05) y se actualiza con una sola
 * sentencia, así que intentos simultáneos no pierden cuentas.
 */
@Service
public class ControlDeIntentos {

  /** {@code bloqueoNuevo}: este fallo fue el que alcanzó el máximo y bloqueó la cuenta. */
  public record ResultadoFallo(int intentos, Instant bloqueadoHasta, boolean bloqueoNuevo) {}

  private final UsuarioRepository usuarios;
  private final PoliticaDeBloqueo politica;

  ControlDeIntentos(UsuarioRepository usuarios, PoliticaDeBloqueo politica) {
    this.usuarios = usuarios;
    this.politica = politica;
  }

  /**
   * Suma un intento dentro de la ventana (o reinicia la ventana si venció) y bloquea al llegar al
   * máximo. Solo el fallo que bloquea se audita: los demás no (ADR 0015).
   */
  @Transactional
  @Auditable(
      accion = Acciones.BLOQUEAR_CUENTA,
      entidad = "usuario",
      entidadId = "#usuarioId",
      condicion = "#result.bloqueoNuevo()")
  public ResultadoFallo registrarFallo(UUID usuarioId) {
    Instant ahora = Instant.now();
    usuarios.registrarFallo(
        usuarioId,
        ahora,
        politica.inicioDeVentana(ahora),
        politica.maximo(),
        politica.bloqueadoHasta(ahora));
    // Misma transacción: la fila sigue bloqueada por nuestra sentencia, así que esto es lo nuestro
    Usuario cuenta = usuarios.findById(usuarioId).orElseThrow();
    return new ResultadoFallo(
        cuenta.getIntentosFallidos(),
        cuenta.getBloqueadoHasta(),
        cuenta.getIntentosFallidos() == politica.maximo());
  }

  /** Un inicio de sesión correcto, o un bloqueo vencido, vuelve el contador a cero. */
  @Transactional
  public void reiniciar(UUID usuarioId) {
    usuarios.reiniciarIntentos(usuarioId, Instant.now());
  }
}
