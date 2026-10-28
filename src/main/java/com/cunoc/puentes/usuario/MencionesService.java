package com.cunoc.puentes.usuario;

import com.cunoc.puentes.usuario.dto.PersonaMencionableDTO;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RN-FOR-10: las cuentas activas que el foro ofrece para mencionar con @. */
@Service
public class MencionesService {

  private final UsuarioRepository usuarios;

  MencionesService(UsuarioRepository usuarios) {
    this.usuarios = usuarios;
  }

  // ponytail: lista completa; buscar y paginar en el servidor si las cuentas pasan de cientos
  @PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO', 'ADMINISTRADOR')")
  @Transactional(readOnly = true)
  public List<PersonaMencionableDTO> mencionables() {
    return usuarios.mencionables();
  }
}
