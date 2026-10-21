package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.usuario.dto.UsuarioAdminDTO;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RN-USR-04: el Administrador aprueba o rechaza el número de colegiado de un profesional externo
 * pendiente. Sin esa aprobación la cuenta no inicia sesión.
 */
@Service
public class VerificacionColegiadoService {

  private final UsuarioRepository usuarios;
  private final VerificadorDeColegiado verificador;
  private final UsuarioMapper mapper;

  VerificacionColegiadoService(
      UsuarioRepository usuarios, VerificadorDeColegiado verificador, UsuarioMapper mapper) {
    this.usuarios = usuarios;
    this.verificador = verificador;
    this.mapper = mapper;
  }

  @Transactional
  public UsuarioAdminDTO aprobar(UUID administradorId, UUID usuarioId) {
    Usuario cuenta = exigirVerificable(usuarioId);
    verificador.aprobar(cuenta.getId(), exigirAdministrador(administradorId).getId());
    return mapper.aAdmin(cuenta);
  }

  @Transactional
  public UsuarioAdminDTO rechazar(UUID administradorId, UUID usuarioId, String motivo) {
    Usuario cuenta = exigirVerificable(usuarioId);
    verificador.rechazar(cuenta.getId(), exigirAdministrador(administradorId).getId(), motivo);
    return mapper.aAdmin(cuenta);
  }

  private Usuario exigirVerificable(UUID usuarioId) {
    Usuario cuenta =
        usuarios
            .bloquear(usuarioId)
            .orElseThrow(
                () ->
                    error(
                        HttpStatus.NOT_FOUND,
                        "usuario-no-encontrado",
                        "Usuario no encontrado",
                        "No existe una cuenta con ese id."));
    if (cuenta.getRol() != Rol.PROFESIONAL_EXTERNO
        || cuenta.getEstado() != EstadoUsuario.PENDIENTE
        || cuenta.getColegiadoVerificadoEn() != null
        || cuenta.getNumeroColegiado() == null
        || cuenta.getCorreoVerificadoEn() == null) {
      throw error(
          HttpStatus.CONFLICT,
          "colegiado-no-verificable",
          "El colegiado no se puede verificar",
          "Solo un profesional externo pendiente, con correo y colegiado, espera verificación.");
    }
    return cuenta;
  }

  private Usuario exigirAdministrador(UUID administradorId) {
    return usuarios
        .findById(administradorId)
        .orElseThrow(
            () ->
                error(
                    HttpStatus.NOT_FOUND,
                    "usuario-no-encontrado",
                    "Usuario no encontrado",
                    "La cuenta ya no existe."));
  }

  private static NegocioException error(
      HttpStatus estado, String tipo, String titulo, String detalle) {
    return new NegocioException(estado, tipo, titulo, detalle);
  }
}
