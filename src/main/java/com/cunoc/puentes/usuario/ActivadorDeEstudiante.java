package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.curso.ActivacionEstudiante;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * RN-USR-02, US-P2-14: un estudiante PENDIENTE se activa solo al ser inscrito por su catedrático.
 * Por eso exige la transacción de la inscripción (ADR 0018): si la inscripción se revierte, la
 * activación y su auditoría también.
 */
@Service
class ActivadorDeEstudiante implements ActivacionEstudiante {

  private final UsuarioRepository repositorio;
  private final EstadoCuentaService cuentas;
  private final CambioDeEstadoDeCuenta cambios;

  ActivadorDeEstudiante(
      UsuarioRepository repositorio, EstadoCuentaService cuentas, CambioDeEstadoDeCuenta cambios) {
    this.repositorio = repositorio;
    this.cuentas = cuentas;
    this.cambios = cambios;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void activar(UUID estudianteId, UUID catedraticoId) {
    if (cuentas.rolSiEstaActiva(catedraticoId).filter(Rol.CATEDRATICO::equals).isEmpty()) {
      throw error(
          HttpStatus.FORBIDDEN,
          "cuenta-no-habilitada",
          "Solo un catedrático activo puede activar a un estudiante.");
    }
    Usuario estudiante =
        repositorio
            .bloquear(estudianteId)
            .filter(cuenta -> cuenta.getRol() == Rol.ESTUDIANTE)
            .orElseThrow(
                () ->
                    error(
                        HttpStatus.NOT_FOUND,
                        "usuario-no-encontrado",
                        "No existe un estudiante con ese id."));
    switch (estudiante.getEstado()) {
      case ACTIVO -> {
        // Ya activo: nada que cambiar ni que auditar
      }
      case INACTIVO ->
          throw error(
              HttpStatus.CONFLICT,
              "cuenta-inactiva",
              "La cuenta está dada de baja; solo un administrador puede reactivarla.");
      case PENDIENTE -> {
        if (estudiante.getCorreoVerificadoEn() == null) {
          throw error(
              HttpStatus.UNPROCESSABLE_CONTENT,
              "correo-no-verificado",
              "El estudiante debe verificar su correo antes de ser activado.");
        }
        cambios.activar(estudianteId);
      }
    }
  }

  private static NegocioException error(HttpStatus estado, String tipo, String detalle) {
    return new NegocioException(estado, tipo, "La cuenta no puede activarse", detalle);
  }
}
