package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los cambios de estado de una cuenta, cada uno auditado con su antes y después (RN-USR-09). Quien
 * llama ya validó la transición y tiene la fila bloqueada.
 */
@Service
class CambioDeEstadoDeCuenta {

  private final UsuarioRepository repositorio;

  CambioDeEstadoDeCuenta(UsuarioRepository repositorio) {
    this.repositorio = repositorio;
  }

  @Transactional
  @Auditable(accion = Acciones.CAMBIAR_ESTADO, entidad = "usuario", entidadId = "#usuarioId")
  public void activar(UUID usuarioId) {
    repositorio.findById(usuarioId).orElseThrow().setEstado(EstadoUsuario.ACTIVO);
  }
}
