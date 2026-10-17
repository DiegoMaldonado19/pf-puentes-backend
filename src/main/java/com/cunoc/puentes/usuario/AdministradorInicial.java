package com.cunoc.puentes.usuario;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * RN-USR-03, DT-BE-15: al arrancar crea la primera cuenta de Administrador con los datos de las
 * variables de entorno, para que alguien pueda invitar a las demás. Es idempotente: se decide por
 * la existencia de cualquier administrador, no por el correo.
 */
@Component
class AdministradorInicial implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdministradorInicial.class);

  static final String VARIABLE_CORREO = "ADMIN_INICIAL_CORREO";
  static final String VARIABLE_NOMBRE = "ADMIN_INICIAL_NOMBRE";
  static final String VARIABLE_CONTRASENA = "ADMIN_INICIAL_CONTRASENA";

  private final UsuarioRepository repositorio;
  private final CreadorDeAdministrador creador;
  private final String correo;
  private final String nombre;
  private final String contrasena;

  AdministradorInicial(
      UsuarioRepository repositorio,
      CreadorDeAdministrador creador,
      @Value("${sgp.administrador-inicial.correo:}") String correo,
      @Value("${sgp.administrador-inicial.nombre:}") String nombre,
      @Value("${sgp.administrador-inicial.contrasena:}") String contrasena) {
    this.repositorio = repositorio;
    this.creador = creador;
    this.correo = correo.strip();
    this.nombre = nombre.strip();
    this.contrasena = contrasena;
  }

  @Override
  public void run(ApplicationArguments argumentos) {
    if (repositorio.existsByRol(Rol.ADMINISTRADOR)) {
      log.debug("Ya existe un administrador: no se crea el administrador inicial");
      return;
    }
    List<String> faltantes = variablesFaltantes();
    if (!faltantes.isEmpty()) {
      log.warn(
          "No existe ningún administrador y faltan las variables {} del administrador inicial:"
              + " nadie podrá invitar a las demás cuentas hasta que se definan",
          faltantes);
      return;
    }
    if (!PoliticaDeContrasena.cumple(contrasena)) {
      throw new IllegalStateException(
          "La contraseña del administrador inicial (%s) no cumple la política: %s"
              .formatted(VARIABLE_CONTRASENA, PoliticaDeContrasena.DESCRIPCION));
    }
    crear();
  }

  private void crear() {
    try {
      Usuario administrador = creador.crear(correo, nombre, contrasena);
      log.info("Administrador inicial creado con id {}", administrador.getId());
    } catch (DataIntegrityViolationException e) {
      // Otra réplica arrancó a la vez y creó la cuenta primero
      if (!repositorio.existsByRol(Rol.ADMINISTRADOR)) {
        throw e;
      }
      log.info("Otra instancia ya creó el administrador inicial");
    }
  }

  private List<String> variablesFaltantes() {
    List<String> faltantes = new ArrayList<>();
    if (!StringUtils.hasText(correo)) {
      faltantes.add(VARIABLE_CORREO);
    }
    if (!StringUtils.hasText(nombre)) {
      faltantes.add(VARIABLE_NOMBRE);
    }
    if (!StringUtils.hasText(contrasena)) {
      faltantes.add(VARIABLE_CONTRASENA);
    }
    return faltantes;
  }
}
