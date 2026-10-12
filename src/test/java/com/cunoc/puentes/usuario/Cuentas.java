package com.cunoc.puentes.usuario;

import java.time.Instant;

/** Fábrica de cuentas válidas para las pruebas; cada una cumple los CHECK de la tabla. */
final class Cuentas {

  static final Instant AHORA = Instant.parse("2026-10-11T15:00:00Z");

  private Cuentas() {}

  static Usuario administrador(String correo) {
    return activa(correo, Rol.ADMINISTRADOR);
  }

  static Usuario estudiante(String correo) {
    return activa(correo, Rol.ESTUDIANTE);
  }

  private static Usuario activa(String correo, Rol rol) {
    Usuario usuario = new Usuario();
    usuario.setCorreo(correo);
    usuario.setNombre("Nombre de " + correo);
    usuario.setContrasenaHash("hash");
    usuario.setRol(rol);
    usuario.setEstado(EstadoUsuario.ACTIVO);
    usuario.setCorreoVerificadoEn(AHORA);
    return usuario;
  }

  static Invitacion invitacion(String correo, Usuario administrador) {
    Invitacion invitacion = new Invitacion();
    invitacion.setCorreo(correo);
    invitacion.setRol(Rol.CATEDRATICO);
    invitacion.setTokenHash(hash(correo));
    invitacion.setExpiraEn(AHORA.plusSeconds(3600));
    invitacion.setAdministrador(administrador);
    return invitacion;
  }

  static TokenVerificacion token(
      Usuario usuario, TipoTokenVerificacion tipo, String semilla, Instant expiraEn) {
    TokenVerificacion token = new TokenVerificacion();
    token.setUsuario(usuario);
    token.setTipo(tipo);
    token.setTokenHash(hash(semilla));
    token.setExpiraEn(expiraEn);
    return token;
  }

  /** 64 caracteres hexadecimales, como un SHA-256; no hace falta que sea el hash real. */
  static String hash(String semilla) {
    return "%64s".formatted(Integer.toHexString(semilla.hashCode())).replace(' ', '0');
  }
}
