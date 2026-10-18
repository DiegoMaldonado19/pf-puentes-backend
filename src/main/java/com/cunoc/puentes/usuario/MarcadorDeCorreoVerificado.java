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

  /** No cambia el estado: activar la cuenta es un acto del catedrático (RN-USR-02). */
  @Transactional
  @Auditable(accion = Acciones.MODIFICAR, entidad = "usuario", entidadId = "#usuarioId")
  public void marcar(UUID usuarioId) {
    repositorio.findById(usuarioId).orElseThrow().setCorreoVerificadoEn(Instant.now());
  }
}
