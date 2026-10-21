package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.usuario.dto.PaginaDTO;
import com.cunoc.puentes.usuario.dto.UsuarioAdminDTO;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** RN-USR-08, US-P2-12: el Administrador lista, consulta, da de baja y reactiva cuentas. */
@Service
public class AdministracionUsuariosService {

  private static final int BUSQUEDA_MAXIMA = 120;
  private static final String COLEGIADO_UNICO = "usuario_colegiado_uk";

  private final UsuarioRepository usuarios;
  private final CambioDeEstadoDeCuenta cambios;
  private final SesionService sesiones;
  private final UsuarioMapper mapper;

  AdministracionUsuariosService(
      UsuarioRepository usuarios,
      CambioDeEstadoDeCuenta cambios,
      SesionService sesiones,
      UsuarioMapper mapper) {
    this.usuarios = usuarios;
    this.cambios = cambios;
    this.sesiones = sesiones;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public PaginaDTO<UsuarioAdminDTO> listar(
      Rol rol, EstadoUsuario estado, String q, Pageable pagina) {
    return PaginaDTO.de(
        usuarios.findAll(filtro(rol, estado, q), pagina(pagina)).map(mapper::aAdmin));
  }

  @Transactional(readOnly = true)
  public UsuarioAdminDTO obtener(UUID usuarioId) {
    return mapper.aAdmin(exigir(usuarioId));
  }

  @Transactional
  public UsuarioAdminDTO darDeBaja(UUID administradorId, UUID usuarioId, String motivo) {
    if (administradorId.equals(usuarioId)) {
      throw error(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "baja-propia-no-permitida",
          "No puedes darte de baja a ti mismo",
          "Pide a otro administrador que dé de baja tu cuenta.");
    }
    Usuario cuenta = bloquear(usuarioId);
    if (cuenta.getEstado() == EstadoUsuario.INACTIVO) {
      throw error(
          HttpStatus.CONFLICT,
          "cuenta-ya-inactiva",
          "La cuenta ya está inactiva",
          "Esa cuenta ya fue dada de baja.");
    }
    if (cuenta.getRol() == Rol.ADMINISTRADOR
        && cuenta.getEstado() == EstadoUsuario.ACTIVO
        && usuarios.countByRolAndEstado(Rol.ADMINISTRADOR, EstadoUsuario.ACTIVO) <= 1) {
      throw error(
          HttpStatus.CONFLICT,
          "ultimo-administrador",
          "No se puede dar de baja al último administrador",
          "El sistema debe conservar al menos un administrador activo.");
    }
    cambios.darDeBaja(cuenta.getId(), administradorId, motivo);
    sesiones.revocarTodas(cuenta.getId());
    return mapper.aAdmin(cuenta);
  }

  @Transactional
  public UsuarioAdminDTO reactivar(UUID usuarioId) {
    Usuario cuenta = bloquear(usuarioId);
    if (cuenta.getEstado() != EstadoUsuario.INACTIVO) {
      throw error(
          HttpStatus.CONFLICT,
          "cuenta-no-inactiva",
          "La cuenta no está inactiva",
          "Solo se reactiva una cuenta dada de baja.");
    }
    try {
      cambios.reactivar(cuenta.getId(), destinoAlReactivar(cuenta));
    } catch (RuntimeException e) {
      if (viola(e, COLEGIADO_UNICO)) {
        throw error(
            HttpStatus.CONFLICT,
            "colegiado-duplicado",
            "El número de colegiado ya está en uso",
            "Ya hay una cuenta activa o pendiente con ese número de colegiado.");
      }
      throw e;
    }
    return mapper.aAdmin(cuenta);
  }

  private Usuario exigir(UUID usuarioId) {
    return usuarios
        .findById(usuarioId)
        .orElseThrow(
            () ->
                error(
                    HttpStatus.NOT_FOUND,
                    "usuario-no-encontrado",
                    "Usuario no encontrado",
                    "No existe una cuenta con ese id."));
  }

  private Usuario bloquear(UUID usuarioId) {
    return usuarios
        .bloquear(usuarioId)
        .orElseThrow(
            () ->
                error(
                    HttpStatus.NOT_FOUND,
                    "usuario-no-encontrado",
                    "Usuario no encontrado",
                    "No existe una cuenta con ese id."));
  }

  private static EstadoUsuario destinoAlReactivar(Usuario cuenta) {
    if (cuenta.getCorreoVerificadoEn() == null) {
      return EstadoUsuario.PENDIENTE;
    }
    if (cuenta.getRol() == Rol.PROFESIONAL_EXTERNO && cuenta.getColegiadoVerificadoEn() == null) {
      return EstadoUsuario.PENDIENTE;
    }
    return EstadoUsuario.ACTIVO;
  }

  private static Specification<Usuario> filtro(Rol rol, EstadoUsuario estado, String q) {
    Specification<Usuario> spec = Specification.unrestricted();
    if (rol != null) {
      spec = spec.and((raiz, consulta, cb) -> cb.equal(raiz.get("rol"), rol));
    }
    if (estado != null) {
      spec = spec.and((raiz, consulta, cb) -> cb.equal(raiz.get("estado"), estado));
    }
    String texto = busqueda(q);
    if (StringUtils.hasText(texto)) {
      String patron = patron(texto.toLowerCase(Locale.ROOT));
      spec =
          spec.and(
              (raiz, consulta, cb) ->
                  cb.or(
                      cb.like(cb.lower(raiz.get("nombre")), patron, '!'),
                      cb.like(cb.lower(raiz.get("correo")), patron, '!')));
    }
    return spec;
  }

  private static String busqueda(String texto) {
    if (texto == null) {
      return "";
    }
    if (texto.length() > BUSQUEDA_MAXIMA) {
      throw error(
          HttpStatus.BAD_REQUEST,
          "busqueda-invalida",
          "Búsqueda inválida",
          "La búsqueda admite hasta 120 caracteres.");
    }
    return texto.strip();
  }

  private static String patron(String texto) {
    return "%" + texto.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }

  private static Pageable pagina(Pageable pagina) {
    return PageRequest.of(
        pagina.getPageNumber(), Math.min(pagina.getPageSize(), 100), Sort.by("creadoEn", "id"));
  }

  private static boolean viola(Throwable e, String restriccion) {
    for (Throwable causa = e; causa != null; causa = causa.getCause()) {
      if (causa instanceof ConstraintViolationException violacion
          && restriccion.equalsIgnoreCase(violacion.getConstraintName())) {
        return true;
      }
    }
    return false;
  }

  private static NegocioException error(
      HttpStatus estado, String tipo, String titulo, String detalle) {
    return new NegocioException(estado, tipo, titulo, detalle);
  }
}
