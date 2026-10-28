package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.common.ErrorCampo;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.curso.CursoService;
import com.cunoc.puentes.curso.dto.AsignacionDTO;
import com.cunoc.puentes.inspeccion.dto.CrearInspeccionDTO;
import com.cunoc.puentes.inspeccion.dto.InspeccionDTO;
import com.cunoc.puentes.inspeccion.puerto.ConsultaFormulario;
import com.cunoc.puentes.inspeccion.puerto.VersionFormularioVista;
import com.cunoc.puentes.sync.IdempotenciaService;
import com.cunoc.puentes.sync.RespuestaIdempotente;
import com.cunoc.puentes.usuario.Rol;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.hibernate.NonUniqueObjectException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
class InicioInspeccionService {

  private static final String PK = "inspeccion_pkey";
  private static final String CORRECCION_VIVA = "inspeccion_correccion_viva_uk";

  private final InspeccionRepository inspecciones;
  private final EscrituraAltaInspeccion escritura;
  private final ReferenciasInspeccion referencias;
  private final ConsultaFormulario formularios;
  private final ValidacionFechaInspeccion fechas;
  private final DistanciaAlPuente distancias;
  private final ConsultaInspeccionService consultas;
  private final CursoService cursos;
  private final InspeccionEnsamblador ensamblador;
  private final IdempotenciaService idempotencia;
  private final JsonMapper json;

  InicioInspeccionService(
      InspeccionRepository inspecciones,
      EscrituraAltaInspeccion escritura,
      ReferenciasInspeccion referencias,
      ConsultaFormulario formularios,
      ValidacionFechaInspeccion fechas,
      DistanciaAlPuente distancias,
      ConsultaInspeccionService consultas,
      CursoService cursos,
      InspeccionEnsamblador ensamblador,
      IdempotenciaService idempotencia,
      JsonMapper json) {
    this.inspecciones = inspecciones;
    this.escritura = escritura;
    this.referencias = referencias;
    this.formularios = formularios;
    this.fechas = fechas;
    this.distancias = distancias;
    this.consultas = consultas;
    this.cursos = cursos;
    this.ensamblador = ensamblador;
    this.idempotencia = idempotencia;
    this.json = json;
  }

  RespuestaIdempotente iniciarIdempotente(
      UUID actorId, Rol rol, CrearInspeccionDTO datos, String encabezado) {
    String clave = encabezado.strip();
    if (clave.codePointCount(0, clave.length()) > 100) {
      throw error(
          HttpStatus.BAD_REQUEST,
          "idempotencia-invalida",
          "Clave de idempotencia inválida",
          "Idempotency-Key no puede superar 100 caracteres.");
    }
    autorizarAlta(actorId, rol, datos, false);
    RespuestaIdempotente respuesta;
    try {
      respuesta = ejecutarAlta(actorId, rol, datos, clave);
    } catch (RuntimeException fallo) {
      // La transacción fallida ya terminó: nunca se consulta desde una sesión abortada por PK.
      if (esConflictoDeAlta(fallo)) {
        respuesta = ejecutarAlta(actorId, rol, datos, clave);
      } else if (esViolacion(fallo, CORRECCION_VIVA)) {
        throw correccionEnCurso();
      } else {
        throw fallo;
      }
    }
    // También cubre una baja o pérdida de permiso mientras se esperaba el advisory lock.
    autorizarAlta(actorId, rol, datos, true);
    return respuesta;
  }

  private RespuestaIdempotente ejecutarAlta(
      UUID actorId, Rol rol, CrearInspeccionDTO datos, String clave) {
    return idempotencia.ejecutar(
        actorId,
        clave,
        HuellaAltaIdempotencia.crear(datos, json),
        Duration.ofSeconds(2),
        () -> {
          autorizarAlta(actorId, rol, datos, false);
          return new RespuestaIdempotente(
              201, json.writeValueAsBytes(iniciar(actorId, rol, datos, true)));
        });
  }

  private void autorizarAlta(
      UUID actorId, Rol rol, CrearInspeccionDTO datos, boolean exigeExistente) {
    exigirRolInspector(rol);
    Inspeccion existente = inspecciones.findById(datos.id()).orElse(null);
    if (existente == null && exigeExistente) {
      throw ConsultaInspeccionService.noEncontrada();
    }
    UUID puenteId = datos.puenteId();
    if (existente != null) {
      mismaDelAutor(existente, actorId, rol);
      puenteId = existente.getPuenteId();
    }
    referencias.puente(puenteId).orElseThrow(InicioInspeccionService::puenteNoEncontrado);
    cursoDelAutor(actorId, rol, datos, puenteId);
  }

  @Transactional
  public InspeccionDTO iniciar(UUID actorId, Rol rol, CrearInspeccionDTO datos) {
    return iniciar(actorId, rol, datos, false);
  }

  private InspeccionDTO iniciar(
      UUID actorId, Rol rol, CrearInspeccionDTO datos, boolean participante) {
    exigirRolInspector(rol);
    Inspeccion existente = inspecciones.findById(datos.id()).orElse(null);
    if (existente != null) {
      return mismaDelAutor(existente, actorId, rol);
    }
    ReferenciasInspeccion.PuenteVista puente =
        referencias
            .puente(datos.puenteId())
            .orElseThrow(InicioInspeccionService::puenteNoEncontrado);
    if (!puente.activo()) {
      throw error(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "puente-inactivo",
          "El puente está inactivo",
          "Un puente dado de baja no se puede inspeccionar.");
    }
    UUID cursoId = cursoDelAutor(actorId, rol, datos, puente.id());
    fechas.exigir(datos.fechaInspeccion(), puente.anioConstruccion());
    GpsInicio gps = gps(datos, puente.id());
    VersionFormularioVista version =
        formularios
            .versionActiva()
            .orElseThrow(
                () ->
                    error(
                        HttpStatus.CONFLICT,
                        "sin-formulario-activo",
                        "No hay formulario activo",
                        "No se puede iniciar una inspección sin una versión activa del formulario."));
    Inspeccion original = originalSiCorrige(datos, puente.id(), actorId, rol);
    Inspeccion inspeccion =
        nueva(datos, actorId, puente.id(), cursoId, version.id(), gps, original);
    inspeccion.setDatos(
        referencias.identificacion(puente.id(), formularios.rutasPorReferencia(version.id())));
    try {
      Inspeccion guardada =
          participante
              ? escritura.persistirParticipando(inspeccion)
              : escritura.persistir(inspeccion);
      return consultas.detalle(guardada.getId(), actorId, rol).conAdvertencias(gps.advertencias());
    } catch (RuntimeException e) {
      if (participante) {
        throw e;
      }
      if (esViolacion(e, CORRECCION_VIVA)) {
        throw correccionEnCurso();
      }
      if (esConflictoDeAlta(e)) {
        return mismaDelAutor(inspecciones.findById(datos.id()).orElseThrow(() -> e), actorId, rol);
      }
      throw e;
    }
  }

  private InspeccionDTO mismaDelAutor(Inspeccion existente, UUID actorId, Rol rol) {
    if (!actorId.equals(existente.getAutorId()) || existente.getEliminadoEn() != null) {
      throw error(
          HttpStatus.CONFLICT,
          "inspeccion-en-conflicto",
          "No se puede crear la inspección",
          "El identificador ya está en uso.");
    }
    return consultas.detalle(existente.getId(), actorId, rol);
  }

  private UUID cursoDelAutor(UUID actorId, Rol rol, CrearInspeccionDTO datos, UUID puenteId) {
    if (rol != Rol.ESTUDIANTE) {
      return null;
    }
    UUID asignacionId = datos.asignacionId();
    if (asignacionId == null) {
      asignacionId =
          referencias
              .asignacionVigente(actorId, puenteId)
              .orElseThrow(InicioInspeccionService::puenteNoAsignado);
    }
    AsignacionDTO asignacion = exigirAsignacion(actorId, asignacionId);
    if (!puenteId.equals(asignacion.puenteId())) {
      throw puenteNoAsignado();
    }
    return asignacion.cursoId();
  }

  private AsignacionDTO exigirAsignacion(UUID estudianteId, UUID asignacionId) {
    try {
      return cursos.exigirAsignacionVigente(estudianteId, asignacionId);
    } catch (NegocioException e) {
      String tipo = e.getBody().getType() == null ? "" : e.getBody().getType().toString();
      if (tipo.endsWith("asignacion-no-encontrada") || tipo.endsWith("curso-no-vigente")) {
        throw puenteNoAsignado();
      }
      throw e;
    }
  }

  private GpsInicio gps(CrearInspeccionDTO datos, UUID puenteId) {
    if (datos.latitud() == null && datos.longitud() == null) {
      return GpsInicio.sinGps();
    }
    if (datos.latitud() == null || datos.longitud() == null) {
      throw new NegocioException(
              HttpStatus.BAD_REQUEST,
              "dto-invalido",
              "Ubicación incompleta",
              "La latitud y la longitud van juntas.")
          .conErrores(List.of(new ErrorCampo("latitud", "La latitud y la longitud van juntas.")));
    }
    BigDecimal metros = distancias.medir(puenteId, datos.latitud(), datos.longitud());
    List<String> advertencias = new ArrayList<>();
    if (distancias.lejana(metros)) {
      advertencias.add(distancias.advertencia(metros));
    }
    return new GpsInicio(
        DistanciaAlPuente.punto(datos.latitud(), datos.longitud()), metros, advertencias);
  }

  private Inspeccion originalSiCorrige(
      CrearInspeccionDTO datos, UUID puenteId, UUID actorId, Rol rol) {
    boolean corrige = datos.supersedeAId() != null;
    String motivo = datos.motivoCorreccion() == null ? null : datos.motivoCorreccion().strip();
    if (!corrige && (motivo == null || motivo.isBlank())) {
      return null;
    }
    if (corrige && (motivo == null || motivo.isBlank())) {
      throw new NegocioException(
              HttpStatus.BAD_REQUEST,
              "dto-invalido",
              "Motivo de corrección requerido",
              "Una corrección exige un motivo.")
          .conErrores(
              List.of(
                  new ErrorCampo("motivo_correccion", "El motivo de corrección es obligatorio.")));
    }
    if (!corrige) {
      throw new NegocioException(
              HttpStatus.BAD_REQUEST,
              "dto-invalido",
              "Corrección incompleta",
              "El motivo de corrección solo aplica al corregir una inspección publicada.")
          .conErrores(
              List.of(
                  new ErrorCampo(
                      "motivo_correccion", "Indique la inspección publicada que corrige.")));
    }
    Inspeccion original =
        inspecciones
            .findByIdAndEliminadoEnIsNull(datos.supersedeAId())
            .orElseThrow(ConsultaInspeccionService::noEncontrada);
    if (original.getEstado() != EstadoInspeccion.PUBLICADA
        || !puenteId.equals(original.getPuenteId())) {
      throw error(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "inspeccion-no-corregible",
          "La inspección no se puede corregir",
          "Solo se corrige una inspección publicada del mismo puente.");
    }
    if (rol == Rol.ESTUDIANTE) {
      referencias
          .asignacionVigente(actorId, puenteId)
          .orElseThrow(InicioInspeccionService::puenteNoAsignado);
    }
    if (inspecciones
        .findBySupersedeA_IdAndEliminadoEnIsNullAndEstadoNot(
            original.getId(), EstadoInspeccion.RECHAZADA)
        .isPresent()) {
      throw correccionEnCurso();
    }
    return original;
  }

  private static Inspeccion nueva(
      CrearInspeccionDTO datos,
      UUID actorId,
      UUID puenteId,
      UUID cursoId,
      UUID versionId,
      GpsInicio gps,
      Inspeccion original) {
    Inspeccion inspeccion = new Inspeccion();
    inspeccion.setId(datos.id());
    inspeccion.setPuenteId(puenteId);
    inspeccion.setAutorId(actorId);
    inspeccion.setFormularioVersionId(versionId);
    inspeccion.setCursoId(cursoId);
    inspeccion.setFechaInspeccion(datos.fechaInspeccion());
    inspeccion.setDispositivoId(datos.dispositivoId().strip());
    inspeccion.setCreadaEnDispositivo(datos.creadaEnDispositivo());
    inspeccion.setDatos(new HashMap<>());
    inspeccion.setUbicacionInicio(gps.punto());
    inspeccion.setDistanciaAPuenteM(gps.metros());
    inspeccion.setUltimaActividadEn(Instant.now());
    if (original != null) {
      inspeccion.setSupersedeA(original);
      inspeccion.setMotivoCorreccion(datos.motivoCorreccion().strip());
    }
    return inspeccion;
  }

  private static void exigirRolInspector(Rol rol) {
    if (rol != Rol.ESTUDIANTE && rol != Rol.PROFESIONAL_EXTERNO && rol != Rol.CATEDRATICO) {
      throw error(
          HttpStatus.FORBIDDEN,
          "rol-no-autorizado",
          "No puede iniciar inspecciones",
          "El Administrador no crea inspecciones.");
    }
  }

  private static boolean esConflictoDeAlta(Throwable error) {
    for (Throwable causa = error; causa != null; causa = causa.getCause()) {
      if (causa instanceof NonUniqueObjectException) {
        return true;
      }
      if (causa instanceof DataIntegrityViolationException && esViolacion(causa, PK)) {
        return true;
      }
      if (esViolacion(causa, PK)) {
        return true;
      }
    }
    return esViolacion(error, PK);
  }

  private static boolean esViolacion(Throwable error, String restriccion) {
    for (Throwable causa = error; causa != null; causa = causa.getCause()) {
      if (causa instanceof ConstraintViolationException violacion
          && restriccion.equalsIgnoreCase(violacion.getConstraintName())) {
        return true;
      }
    }
    return false;
  }

  private static NegocioException puenteNoEncontrado() {
    return error(
        HttpStatus.NOT_FOUND,
        "puente-no-encontrado",
        "Puente no encontrado",
        "No existe un puente con el id indicado.");
  }

  private static NegocioException puenteNoAsignado() {
    return error(
        HttpStatus.FORBIDDEN,
        "puente-no-asignado",
        "Puente no asignado",
        "No tiene una asignación vigente para inspeccionar ese puente.");
  }

  private static NegocioException correccionEnCurso() {
    return error(
        HttpStatus.CONFLICT,
        "correccion-en-curso",
        "Ya hay una corrección en curso",
        "Esa inspección ya tiene una corrección viva.");
  }

  private static NegocioException error(
      HttpStatus estado, String tipo, String titulo, String detalle) {
    return new NegocioException(estado, tipo, titulo, detalle);
  }

  private record GpsInicio(
      org.locationtech.jts.geom.Point punto, BigDecimal metros, List<String> advertencias) {
    static GpsInicio sinGps() {
      return new GpsInicio(null, null, List.of());
    }
  }
}
