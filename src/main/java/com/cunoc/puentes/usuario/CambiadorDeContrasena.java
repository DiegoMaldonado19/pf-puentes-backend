package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda el hash nuevo (BCrypt 12, RN-USR-06). El saneador omite {@code contrasena_hash} por nombre
 * (DT-SEC-12). Quien llama ya validó y tiene la fila bloqueada.
 */
@Service
class CambiadorDeContrasena {

  private final UsuarioRepository repositorio;
  private final PasswordEncoder codificador;

  CambiadorDeContrasena(UsuarioRepository repositorio, PasswordEncoder codificador) {
    this.repositorio = repositorio;
    this.codificador = codificador;
  }

  @Transactional
  @Auditable(accion = Acciones.MODIFICAR, entidad = "usuario", entidadId = "#usuarioId")
  public void cambiar(UUID usuarioId, String contrasenaNueva) {
    repositorio
        .findById(usuarioId)
        .orElseThrow()
        .setContrasenaHash(codificador.encode(contrasenaNueva));
  }
}
