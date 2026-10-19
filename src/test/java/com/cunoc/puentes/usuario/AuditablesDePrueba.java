package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicios y endpoints de prueba para el aspecto de auditoría (US-P2-03). Viven en {@code
 * usuario/} porque cambian la entidad {@link Usuario}, cuyos setters son del paquete.
 *
 * <p>Sirven también de ejemplo de uso: la anotación va en el método de servicio.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AuditablesDePrueba {

  @Bean
  UsuariosAuditables usuariosAuditables(UsuarioRepository repositorio) {
    return new UsuariosAuditables(repositorio);
  }

  @Bean
  OperacionCompuesta operacionCompuesta(UsuariosAuditables usuarios) {
    return new OperacionCompuesta(usuarios);
  }

  public static class UsuariosAuditables {

    private final UsuarioRepository repositorio;

    UsuariosAuditables(UsuarioRepository repositorio) {
      this.repositorio = repositorio;
    }

    @Transactional
    @Auditable(accion = Acciones.MODIFICAR, entidad = "usuario", entidadId = "#id")
    public void renombrar(UUID id, String nombre) {
      repositorio.findById(id).orElseThrow().setNombre(nombre);
    }

    @Transactional
    @Auditable(
        accion = Acciones.MODIFICAR,
        entidad = "usuario",
        entidadId = "#id",
        condicion = "#audita")
    public void renombrarSiAudita(UUID id, String nombre, boolean audita) {
      repositorio.findById(id).orElseThrow().setNombre(nombre);
    }

    @Transactional
    @Auditable(accion = Acciones.CREAR, entidad = "usuario", entidadId = "#result.id")
    public Usuario crear(String correo) {
      return repositorio.save(Cuentas.estudiante(correo));
    }

    @Transactional
    @Auditable(accion = Acciones.MODIFICAR, entidad = "usuario", entidadId = "#id")
    public void cambiarContrasena(UUID id, String hashNuevo) {
      repositorio.findById(id).orElseThrow().setContrasenaHash(hashNuevo);
    }

    @Transactional
    @Auditable(accion = Acciones.MODIFICAR, entidad = "usuario", entidadId = "#id")
    public void renombrarYFallar(UUID id, String nombre) {
      repositorio.findById(id).orElseThrow().setNombre(nombre);
      throw new IllegalStateException("falla después de modificar");
    }

    // Sin @Transactional: el aspecto abre la transacción
    @Auditable(
        accion = Acciones.DAR_DE_BAJA,
        entidad = "usuario",
        entidadId = "#id",
        motivo = "#motivo")
    public void darDeBaja(UUID id, String motivo) {
      Usuario usuario = repositorio.findById(id).orElseThrow();
      usuario.setEstado(EstadoUsuario.INACTIVO);
      usuario.setDesactivadoEn(Instant.now());
      usuario.setMotivoBaja(motivo);
    }

    @Transactional
    @Auditable(accion = Acciones.BLOQUEAR_CUENTA, entidad = "usuario", entidadId = "#id")
    public void bloquear(UUID id) {
      repositorio.findById(id).orElseThrow().setBloqueadoHasta(Instant.now().plusSeconds(900));
    }

    // Un intento fallido NO es auditable: solo el bloqueo lo es (RN-USR-07)
    @Transactional
    public void registrarIntentoFallido(UUID id) {
      Usuario usuario = repositorio.findById(id).orElseThrow();
      usuario.setIntentosFallidos(usuario.getIntentosFallidos() + 1);
    }
  }

  public static class OperacionCompuesta {

    private final UsuariosAuditables usuarios;

    OperacionCompuesta(UsuariosAuditables usuarios) {
      this.usuarios = usuarios;
    }

    /** Dos métodos auditables en una misma operación de negocio. */
    @Transactional
    public void renombrarYBloquear(UUID id, String nombre) {
      usuarios.renombrar(id, nombre);
      usuarios.bloquear(id);
    }

    /** Igual, pero la operación misma es auditable: sus tres filas comparten correlación. */
    @Auditable(accion = Acciones.MODIFICAR, entidad = "usuario", entidadId = "#id")
    public void renombrarYBloquearAuditado(UUID id, String nombre) {
      usuarios.renombrar(id, nombre);
      usuarios.bloquear(id);
    }
  }
}
