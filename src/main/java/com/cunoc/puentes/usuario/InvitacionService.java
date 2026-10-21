package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.usuario.dto.AceptarInvitacionDTO;
import com.cunoc.puentes.usuario.dto.CrearInvitacionDTO;
import com.cunoc.puentes.usuario.dto.CuentaRegistradaDTO;
import com.cunoc.puentes.usuario.dto.InvitacionDTO;
import com.cunoc.puentes.usuario.dto.InvitacionPublicaDTO;
import com.cunoc.puentes.usuario.dto.PaginaDTO;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RN-USR-03: el Administrador invita, lista y revoca; el invitado consulta y acepta el enlace. */
@Service
public class InvitacionService {

  private static final Set<Rol> INVITABLES =
      Set.of(Rol.PROFESIONAL_EXTERNO, Rol.CATEDRATICO, Rol.ADMINISTRADOR);
  private static final String CORREO_UNICO = "usuario_correo_uk";
  private static final String COLEGIADO_UNICO = "usuario_colegiado_uk";
  private static final String ABIERTA_UNICA = "invitacion_abierta_uk";

  private final UsuarioRepository usuarios;
  private final InvitacionRepository invitaciones;
  private final EmisorDeInvitacion emisor;
  private final RevocadorDeInvitacion revocador;
  private final CreadorDeInvitado creador;
  private final AceptadorDeInvitacion aceptador;
  private final InvitacionMapper invitacionMapper;
  private final UsuarioMapper usuarioMapper;

  InvitacionService(
      UsuarioRepository usuarios,
      InvitacionRepository invitaciones,
      EmisorDeInvitacion emisor,
      RevocadorDeInvitacion revocador,
      CreadorDeInvitado creador,
      AceptadorDeInvitacion aceptador,
      InvitacionMapper invitacionMapper,
      UsuarioMapper usuarioMapper) {
    this.usuarios = usuarios;
    this.invitaciones = invitaciones;
    this.emisor = emisor;
    this.revocador = revocador;
    this.creador = creador;
    this.aceptador = aceptador;
    this.invitacionMapper = invitacionMapper;
    this.usuarioMapper = usuarioMapper;
  }

  @Transactional
  public InvitacionDTO crear(UUID administradorId, CrearInvitacionDTO datos) {
    if (!INVITABLES.contains(datos.rol())) {
      throw error(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "rol-no-invitable",
          "El rol no se invita",
          "Los estudiantes se registran solos; los demás roles entran por invitación.");
    }
    if (usuarios.existePorCorreo(datos.correo())) {
      throw correoYaRegistrado();
    }
    Usuario administrador = exigirAdministrador(administradorId);
    invitaciones
        .buscarAbierta(datos.correo())
        .ifPresent(abierta -> cerrarORechazar(abierta.getId(), administrador));
    try {
      return aDTO(emisor.emitir(datos.correo(), datos.rol(), administrador));
    } catch (DataIntegrityViolationException e) {
      if (viola(e, ABIERTA_UNICA)) {
        throw invitacionAbierta();
      }
      if (viola(e, CORREO_UNICO)) {
        throw correoYaRegistrado();
      }
      throw e;
    }
  }

  @Transactional(readOnly = true)
  public PaginaDTO<InvitacionDTO> listar(EstadoInvitacion estado, Pageable pagina) {
    Instant ahora = Instant.now();
    return PaginaDTO.de(
        invitaciones
            .findAll(filtro(estado, ahora), pagina(pagina))
            .map(
                invitacion ->
                    invitacionMapper.aDTO(invitacion, EstadoInvitacion.de(invitacion, ahora))));
  }

  @Transactional
  public InvitacionDTO revocar(UUID administradorId, UUID invitacionId) {
    Invitacion invitacion =
        invitaciones.bloquear(invitacionId).orElseThrow(InvitacionService::invitacionNoEncontrada);
    exigirAbierta(invitacion);
    return aDTO(revocador.revocar(invitacion.getId(), exigirAdministrador(administradorId)));
  }

  @Transactional(readOnly = true)
  public InvitacionPublicaDTO consultar(String token) {
    return invitacionMapper.aPublica(
        exigirVigente(invitaciones.buscarPorHash(TokenOpaco.hash(token))));
  }

  @Transactional
  public CuentaRegistradaDTO aceptar(AceptarInvitacionDTO datos) {
    Invitacion invitacion =
        exigirVigente(invitaciones.findByTokenHash(TokenOpaco.hash(datos.token())));
    if (invitacion.getRol() == Rol.PROFESIONAL_EXTERNO && datos.numeroColegiado() == null) {
      throw error(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "colegiado-requerido",
          "Falta el número de colegiado",
          "El profesional externo debe indicar su número de colegiado.");
    }
    if (usuarios.existePorCorreo(invitacion.getCorreo())) {
      throw correoYaRegistrado();
    }
    Usuario cuenta;
    try {
      cuenta =
          creador.crear(
              invitacion.getCorreo(),
              datos.nombre(),
              datos.contrasena(),
              invitacion.getRol(),
              datos.numeroColegiado());
    } catch (DataIntegrityViolationException e) {
      if (viola(e, CORREO_UNICO)) {
        throw correoYaRegistrado();
      }
      if (viola(e, COLEGIADO_UNICO)) {
        throw error(
            HttpStatus.CONFLICT,
            "colegiado-duplicado",
            "El número de colegiado ya está en uso",
            "Ya hay una cuenta activa o pendiente con ese número de colegiado.");
      }
      throw e;
    }
    aceptador.aceptar(invitacion.getId(), cuenta);
    return usuarioMapper.aDTO(cuenta);
  }

  private void cerrarORechazar(UUID invitacionId, Usuario administrador) {
    Invitacion abierta = invitaciones.bloquear(invitacionId).orElseThrow();
    if (abierta.getAceptadaEn() != null || abierta.getRevocadaEn() != null) {
      return;
    }
    if (abierta.getExpiraEn().isAfter(Instant.now())) {
      throw invitacionAbierta();
    }
    revocador.revocar(abierta.getId(), administrador);
  }

  private static void exigirAbierta(Invitacion invitacion) {
    if (invitacion.getAceptadaEn() != null || invitacion.getRevocadaEn() != null) {
      throw error(
          HttpStatus.CONFLICT,
          "invitacion-cerrada",
          "La invitación ya está cerrada",
          "La invitación ya fue aceptada o revocada.");
    }
  }

  private static Invitacion exigirVigente(Optional<Invitacion> candidata) {
    Instant ahora = Instant.now();
    return candidata
        .filter(invitacion -> invitacion.getAceptadaEn() == null)
        .filter(invitacion -> invitacion.getRevocadaEn() == null)
        .filter(invitacion -> invitacion.getExpiraEn().isAfter(ahora))
        .orElseThrow(InvitacionService::invitacionInvalida);
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

  private InvitacionDTO aDTO(Invitacion invitacion) {
    return invitacionMapper.aDTO(invitacion, EstadoInvitacion.de(invitacion, Instant.now()));
  }

  private static Specification<Invitacion> filtro(EstadoInvitacion estado, Instant ahora) {
    if (estado == null) {
      return Specification.unrestricted();
    }
    return (raiz, consulta, cb) ->
        switch (estado) {
          case ACEPTADA -> cb.isNotNull(raiz.get("aceptadaEn"));
          case REVOCADA -> cb.isNotNull(raiz.get("revocadaEn"));
          case ABIERTA ->
              cb.and(
                  cb.isNull(raiz.get("aceptadaEn")),
                  cb.isNull(raiz.get("revocadaEn")),
                  cb.greaterThan(raiz.get("expiraEn"), ahora));
          case VENCIDA ->
              cb.and(
                  cb.isNull(raiz.get("aceptadaEn")),
                  cb.isNull(raiz.get("revocadaEn")),
                  cb.lessThanOrEqualTo(raiz.get("expiraEn"), ahora));
        };
  }

  private static Pageable pagina(Pageable pagina) {
    return PageRequest.of(
        pagina.getPageNumber(),
        Math.min(pagina.getPageSize(), 100),
        Sort.by(Sort.Direction.DESC, "creadoEn", "id"));
  }

  private static boolean viola(DataIntegrityViolationException e, String restriccion) {
    for (Throwable causa = e; causa != null; causa = causa.getCause()) {
      if (causa instanceof ConstraintViolationException violacion
          && restriccion.equalsIgnoreCase(violacion.getConstraintName())) {
        return true;
      }
    }
    return false;
  }

  private static NegocioException invitacionAbierta() {
    return error(
        HttpStatus.CONFLICT,
        "invitacion-abierta",
        "Ya hay una invitación abierta",
        "Ese correo ya tiene una invitación vigente.");
  }

  private static NegocioException invitacionNoEncontrada() {
    return error(
        HttpStatus.NOT_FOUND,
        "invitacion-no-encontrada",
        "Invitación no encontrada",
        "No existe una invitación con ese id.");
  }

  private static NegocioException invitacionInvalida() {
    return error(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "invitacion-invalida",
        "La invitación no es válida",
        "La invitación no es válida o ya venció.");
  }

  private static NegocioException correoYaRegistrado() {
    return error(
        HttpStatus.CONFLICT,
        "correo-ya-registrado",
        "El correo ya está registrado",
        "Ya existe una cuenta con ese correo.");
  }

  private static NegocioException error(
      HttpStatus estado, String tipo, String titulo, String detalle) {
    return new NegocioException(estado, tipo, titulo, detalle);
  }
}
