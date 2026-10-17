package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.usuario.dto.PerfilDTO;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta del perfil propio: autorización por fila, el id sale del token (DT-SEC-04). */
@Service
public class PerfilService {

  private final UsuarioRepository usuarios;
  private final UsuarioMapper mapper;

  PerfilService(UsuarioRepository usuarios, UsuarioMapper mapper) {
    this.usuarios = usuarios;
    this.mapper = mapper;
  }

  /** Solo existe "la mía": no hay parámetro con el id de otra persona. */
  @PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO', 'ADMINISTRADOR')")
  @Transactional(readOnly = true)
  public PerfilDTO obtener(UUID usuarioAutenticado) {
    return usuarios
        .findById(usuarioAutenticado)
        .map(mapper::aPerfil)
        .orElseThrow(
            () ->
                new NegocioException(
                    HttpStatus.NOT_FOUND,
                    "usuario-no-encontrado",
                    "Usuario no encontrado",
                    "La cuenta ya no existe."));
  }
}
