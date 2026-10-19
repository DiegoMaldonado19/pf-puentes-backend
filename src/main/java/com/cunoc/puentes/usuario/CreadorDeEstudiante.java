package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inserta la cuenta del registro autónomo; la fila de auditoría la escribe el aspecto. */
@Service
class CreadorDeEstudiante {

  private final UsuarioRepository repositorio;
  private final PasswordEncoder codificador;

  CreadorDeEstudiante(UsuarioRepository repositorio, PasswordEncoder codificador) {
    this.repositorio = repositorio;
    this.codificador = codificador;
  }

  /** RN-USR-02: siempre Estudiante, PENDIENTE y con el correo sin verificar. */
  @Transactional
  @Auditable(accion = Acciones.CREAR, entidad = "usuario", entidadId = "#result.id")
  public Usuario crear(String correo, String nombre, String contrasena) {
    Usuario estudiante = new Usuario();
    estudiante.setCorreo(correo);
    estudiante.setNombre(nombre);
    estudiante.setContrasenaHash(codificador.encode(contrasena));
    estudiante.setRol(Rol.ESTUDIANTE);
    estudiante.setEstado(EstadoUsuario.PENDIENTE);
    return repositorio.saveAndFlush(estudiante);
  }
}
