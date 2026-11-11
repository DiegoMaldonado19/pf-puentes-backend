package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registra cuándo se verificó el correo (RN-USR-01); la auditoría guarda el antes y el después. */
@Service
class MarcadorDeCorreoVerificado {

  private final UsuarioRepository repositorio;

  MarcadorDeCorreoVerificado(UsuarioRepository repositorio) {
    this.repositorio = repositorio;
  }

  /** Marca el correo como verificado y activa cuentas pendientes al confirmar su correo. */
  @Transactional
  @Auditable(accion = Acciones.MODIFICAR, entidad = "usuario", entidadId = "#usuarioId")
  public void marcar(UUID usuarioId) {
    Usuario usuario = repositorio.findById(usuarioId).orElseThrow();
    usuario.setCorreoVerificadoEn(Instant.now());
    if (usuario.getEstado() == EstadoUsuario.PENDIENTE) {
      usuario.setEstado(EstadoUsuario.ACTIVO);
    }
  }
}
