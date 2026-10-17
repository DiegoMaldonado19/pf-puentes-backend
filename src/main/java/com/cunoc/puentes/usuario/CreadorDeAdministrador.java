package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inserta la cuenta del administrador inicial; la fila de auditoría la escribe el aspecto. */
@Service
class CreadorDeAdministrador {

  private final UsuarioRepository repositorio;
  private final PasswordEncoder codificador;

  CreadorDeAdministrador(UsuarioRepository repositorio, PasswordEncoder codificador) {
    this.repositorio = repositorio;
    this.codificador = codificador;
  }

  /** Sin sesión, la auditoría la atribuye al Sistema (usuario nulo). */
  @Transactional
  @Auditable(accion = Acciones.CREAR, entidad = "usuario", entidadId = "#result.id")
  public Usuario crear(String correo, String nombre, String contrasena) {
    Usuario administrador = new Usuario();
    administrador.setCorreo(correo);
    administrador.setNombre(nombre);
    administrador.setContrasenaHash(codificador.encode(contrasena));
    administrador.setRol(Rol.ADMINISTRADOR);
    administrador.setEstado(EstadoUsuario.ACTIVO);
    administrador.setCorreoVerificadoEn(Instant.now());
    return repositorio.saveAndFlush(administrador);
  }
}
