package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RN-USR-04: deja rastro de quién verificó el colegiado o, si se rechaza, de la baja. Quien llama
 * ya validó la transición y tiene la fila bloqueada.
 */
@Service
class VerificadorDeColegiado {

  private final UsuarioRepository repositorio;

  VerificadorDeColegiado(UsuarioRepository repositorio) {
    this.repositorio = repositorio;
  }

  @Transactional
  @Auditable(accion = Acciones.VERIFICAR_COLEGIADO, entidad = "usuario", entidadId = "#usuarioId")
  public void aprobar(UUID usuarioId, UUID administradorId) {
    Usuario cuenta = repositorio.findById(usuarioId).orElseThrow();
    cuenta.setColegiadoVerificadoEn(Instant.now());
    cuenta.setColegiadoVerificadoPor(repositorio.findById(administradorId).orElseThrow());
    cuenta.setEstado(EstadoUsuario.ACTIVO);
  }

  @Transactional
  @Auditable(
      accion = Acciones.DAR_DE_BAJA,
      entidad = "usuario",
      entidadId = "#usuarioId",
      motivo = "#motivo")
  public void rechazar(UUID usuarioId, UUID administradorId, String motivo) {
    Usuario cuenta = repositorio.findById(usuarioId).orElseThrow();
    Instant ahora = Instant.now();
    cuenta.setEstado(EstadoUsuario.INACTIVO);
    cuenta.setDesactivadoEn(ahora);
    cuenta.setDesactivadoPor(repositorio.findById(administradorId).orElseThrow());
    cuenta.setMotivoBaja(motivo);
  }
}
