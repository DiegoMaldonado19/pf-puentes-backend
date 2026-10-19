package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.usuario.dto.CuentaRegistradaDTO;
import com.cunoc.puentes.usuario.dto.RegistrarEstudianteDTO;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RN-USR-02: el registro autónomo solo crea Estudiantes pendientes de activación. */
@Service
public class RegistroService {

  private static final String CORREO_UNICO = "usuario_correo_uk";

  private final UsuarioRepository usuarios;
  private final CreadorDeEstudiante creador;
  private final VerificacionCorreoService verificacion;
  private final UsuarioMapper mapper;

  RegistroService(
      UsuarioRepository usuarios,
      CreadorDeEstudiante creador,
      VerificacionCorreoService verificacion,
      UsuarioMapper mapper) {
    this.usuarios = usuarios;
    this.creador = creador;
    this.verificacion = verificacion;
    this.mapper = mapper;
  }

  @Transactional
  public CuentaRegistradaDTO registrar(RegistrarEstudianteDTO datos) {
    if (usuarios.existePorCorreo(datos.correo())) {
      throw correoYaRegistrado();
    }
    Usuario cuenta;
    try {
      cuenta = creador.crear(datos.correo(), datos.nombre(), datos.contrasena());
    } catch (DataIntegrityViolationException e) {
      // Otro registro con el mismo correo ganó la carrera: usuario_correo_uk lo detiene
      if (violaElCorreoUnico(e)) {
        throw correoYaRegistrado();
      }
      throw e;
    }
    verificacion.emitir(cuenta);
    return mapper.aDTO(cuenta);
  }

  private static boolean violaElCorreoUnico(DataIntegrityViolationException e) {
    for (Throwable causa = e; causa != null; causa = causa.getCause()) {
      if (causa instanceof ConstraintViolationException violacion
          && CORREO_UNICO.equalsIgnoreCase(violacion.getConstraintName())) {
        return true;
      }
    }
    return false;
  }

  private static NegocioException correoYaRegistrado() {
    return new NegocioException(
        HttpStatus.CONFLICT,
        "correo-ya-registrado",
        "El correo ya está registrado",
        "Ya existe una cuenta con ese correo.");
  }
}
