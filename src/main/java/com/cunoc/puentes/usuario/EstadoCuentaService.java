package com.cunoc.puentes.usuario;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lo que los demás módulos pueden saber de una cuenta sin tocar el repositorio (RN-USR-10). */
@Service
public class EstadoCuentaService {

  private final UsuarioRepository repositorio;

  EstadoCuentaService(UsuarioRepository repositorio) {
    this.repositorio = repositorio;
  }

  /** Rol vigente de la cuenta, o vacío si no existe o no está ACTIVA (RN-USR-08, RN-USR-10). */
  @Transactional(readOnly = true)
  public Optional<Rol> rolSiEstaActiva(UUID usuarioId) {
    return repositorio.buscarRolActivo(usuarioId);
  }
}
