package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea la cuenta al aceptar una invitación. El correo se da por verificado porque el enlace llegó a
 * esa bandeja (RN-USR-01). El profesional externo nace PENDIENTE a la espera del colegiado
 * (RN-USR-04); los demás nacen ACTIVO.
 */
@Service
class CreadorDeInvitado {

  private final UsuarioRepository repositorio;
  private final PasswordEncoder codificador;

  CreadorDeInvitado(UsuarioRepository repositorio, PasswordEncoder codificador) {
    this.repositorio = repositorio;
    this.codificador = codificador;
  }

  @Transactional
  @Auditable(accion = Acciones.CREAR, entidad = "usuario", entidadId = "#result.id")
  public Usuario crear(
      String correo, String nombre, String contrasena, Rol rol, String numeroColegiado) {
    Usuario cuenta = new Usuario();
    cuenta.setCorreo(correo);
    cuenta.setNombre(nombre);
    cuenta.setContrasenaHash(codificador.encode(contrasena));
    cuenta.setRol(rol);
    cuenta.setCorreoVerificadoEn(Instant.now());
    if (rol == Rol.PROFESIONAL_EXTERNO) {
      cuenta.setNumeroColegiado(numeroColegiado);
      cuenta.setEstado(EstadoUsuario.PENDIENTE);
    } else {
      cuenta.setEstado(EstadoUsuario.ACTIVO);
    }
    return repositorio.saveAndFlush(cuenta);
  }
}
