package com.cunoc.puentes.curso;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import com.cunoc.puentes.curso.dto.*;
import com.cunoc.puentes.usuario.EstadoCuentaService;
import com.cunoc.puentes.usuario.Rol;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CursoService {
  private final CursoRepository cursos;
  private final InscripcionRepository inscripciones;
  private final AsignacionRepository asignaciones;
  private final ReferenciasCursoRepository referencias;
  private final CursoMapper mapper;
  private final ObjectProvider<ActivacionEstudiante> activacion;
  private final EstadoCuentaService cuentas;

  CursoService(
      CursoRepository cursos,
      InscripcionRepository inscripciones,
      AsignacionRepository asignaciones,
      ReferenciasCursoRepository referencias,
      CursoMapper mapper,
      ObjectProvider<ActivacionEstudiante> activacion,
      EstadoCuentaService cuentas) {
    this.cursos = cursos;
    this.inscripciones = inscripciones;
    this.asignaciones = asignaciones;
    this.referencias = referencias;
    this.mapper = mapper;
    this.activacion = activacion;
    this.cuentas = cuentas;
  }

  public Page<CursoDTO> listar(UUID usuarioId, Pageable pagina) {
    exigirCuenta(usuarioId, "CATEDRATICO");
    return cursos.findByCatedraticoId(usuarioId, pagina(pagina)).map(mapper::aDTO);
  }

  public CursoDTO obtener(UUID usuarioId, UUID cursoId) {
    exigirCuenta(usuarioId, "CATEDRATICO");
    return mapper.aDTO(propio(usuarioId, cursoId, false));
  }

  @Transactional
  @Auditable(accion = Acciones.CREAR, entidad = "curso", entidadId = "#result.id")
  public CursoDTO crear(UUID usuarioId, GuardarCursoDTO datos) {
    exigirCuenta(usuarioId, "CATEDRATICO");
    Curso curso = new Curso();
    mapper.actualizar(datos, curso);
    curso.setCatedraticoId(usuarioId);
    return mapper.aDTO(cursos.saveAndFlush(curso));
  }

  @Transactional
  @Auditable(accion = Acciones.MODIFICAR, entidad = "curso", entidadId = "#cursoId")
  public CursoDTO actualizar(UUID usuarioId, UUID cursoId, GuardarCursoDTO datos) {
    exigirCuenta(usuarioId, "CATEDRATICO");
    Curso curso = propio(usuarioId, cursoId, true);
    mapper.actualizar(datos, curso);
    return mapper.aDTO(cursos.saveAndFlush(curso));
  }

  public Page<InscripcionDTO> listarInscripciones(UUID usuarioId, UUID cursoId, Pageable pagina) {
    exigirCuenta(usuarioId, "CATEDRATICO");
    propio(usuarioId, cursoId, false);
    var resultado = inscripciones.findByCursoId(cursoId, pagina(pagina));
    var nombres =
        referencias.nombresEstudiantes(
            resultado.stream().map(Inscripcion::getEstudianteId).collect(Collectors.toSet()));
    return resultado.map(i -> mapper.aDTO(i, nombres.get(i.getEstudianteId())));
  }

  @Transactional
  @Auditable(accion = Acciones.CREAR, entidad = "inscripcion", entidadId = "#result.id")
  public InscripcionDTO inscribir(UUID usuarioId, UUID cursoId, InscribirEstudianteDTO datos) {
    exigirCuenta(usuarioId, "CATEDRATICO");
    Curso curso = propio(usuarioId, cursoId, true);
    exigirVigente(curso);
    if (inscripciones.existsByCursoIdAndEstudianteId(cursoId, datos.estudianteId())) {
      throw error(
          HttpStatus.CONFLICT,
          "inscripcion-duplicada",
          "El estudiante ya está inscrito en este curso.");
    }
    var cuenta =
        referencias
            .buscarCuenta(datos.estudianteId(), true)
            .orElseThrow(
                () ->
                    error(
                        HttpStatus.NOT_FOUND,
                        "estudiante-no-encontrado",
                        "No existe el estudiante."));
    if (!"ESTUDIANTE".equals(cuenta.rol())
        || "INACTIVO".equals(cuenta.estado())
        || cuenta.correoVerificadoEn() == null) {
      throw error(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "estudiante-no-elegible",
          "Se requiere un estudiante no inactivo con correo verificado.");
    }
    if ("PENDIENTE".equals(cuenta.estado())) {
      ActivacionEstudiante servicio = activacion.getIfAvailable();
      if (servicio == null) {
        throw error(
            HttpStatus.SERVICE_UNAVAILABLE,
            "activacion-no-disponible",
            "El servicio de activación de P2 aún no está integrado.");
      }
      servicio.activar(datos.estudianteId(), usuarioId);
      exigirCuenta(datos.estudianteId(), "ESTUDIANTE");
    }
    Inscripcion inscripcion = new Inscripcion();
    inscripcion.setCurso(curso);
    inscripcion.setEstudianteId(datos.estudianteId());
    return mapper.aDTO(
        inscripciones.saveAndFlush(inscripcion),
        referencias.nombresEstudiantes(Set.of(datos.estudianteId())).get(datos.estudianteId()));
  }

  public Page<AsignacionDTO> listarAsignaciones(
      UUID usuarioId, UUID cursoId, UUID inscripcionId, Pageable pagina) {
    exigirCuenta(usuarioId, "CATEDRATICO");
    propio(usuarioId, cursoId, false);
    inscripcion(cursoId, inscripcionId);
    return conPuentes(asignaciones.findByInscripcionId(inscripcionId, pagina(pagina)));
  }

  @Transactional
  @Auditable(accion = Acciones.CREAR, entidad = "asignacion", entidadId = "#result.id")
  public AsignacionDTO asignar(
      UUID usuarioId, UUID cursoId, UUID inscripcionId, AsignarPuenteDTO datos) {
    exigirCuenta(usuarioId, "CATEDRATICO");
    exigirVigente(propio(usuarioId, cursoId, true));
    Inscripcion inscripcion = inscripcion(cursoId, inscripcionId);
    exigirCuenta(inscripcion.getEstudianteId(), "ESTUDIANTE");
    if (!referencias.puenteActivo(datos.puenteId())) {
      throw error(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "puente-no-disponible",
          "El puente no existe o está inactivo.");
    }
    if (asignaciones.existsByInscripcionIdAndPuenteId(inscripcionId, datos.puenteId())) {
      throw error(
          HttpStatus.CONFLICT,
          "asignacion-duplicada",
          "El puente ya está asignado a esta inscripción.");
    }
    Asignacion asignacion = new Asignacion();
    asignacion.setInscripcion(inscripcion);
    asignacion.setPuenteId(datos.puenteId());
    return mapper.aDTO(
        asignaciones.saveAndFlush(asignacion),
        referencias.datosPuentes(Set.of(datos.puenteId())).get(datos.puenteId()));
  }

  /**
   * Contrato 6 para P6: conserva curso e inscripción, aunque dos cursos asignen el mismo puente.
   */
  public Page<AsignacionDTO> consultarAsignacionesVigentes(UUID estudianteId, Pageable pagina) {
    exigirCuenta(estudianteId, "ESTUDIANTE");
    return conPuentes(asignaciones.buscarVigentes(estudianteId, pagina(pagina)));
  }

  /** Contrato 6 para P5: usar el autor autenticado y persistir el contexto académico acordado. */
  public AsignacionDTO exigirAsignacionVigente(UUID estudianteId, UUID asignacionId) {
    exigirCuenta(estudianteId, "ESTUDIANTE");
    Asignacion asignacion =
        asignaciones
            .findByIdAndInscripcionEstudianteId(asignacionId, estudianteId)
            .orElseThrow(
                () ->
                    error(
                        HttpStatus.NOT_FOUND,
                        "asignacion-no-encontrada",
                        "La asignación no existe o no le pertenece."));
    exigirVigente(asignacion.getInscripcion().getCurso());
    if (!referencias.puenteActivo(asignacion.getPuenteId())) {
      throw error(
          HttpStatus.UNPROCESSABLE_CONTENT, "puente-no-disponible", "El puente está inactivo.");
    }
    return mapper.aDTO(
        asignacion,
        referencias.datosPuentes(Set.of(asignacion.getPuenteId())).get(asignacion.getPuenteId()));
  }

  public Page<EstudianteElegibleDTO> estudiantesElegibles(UUID usuarioId, Pageable pagina) {
    return estudiantesElegibles(usuarioId, pagina, "");
  }

  public Page<EstudianteElegibleDTO> estudiantesElegibles(
      UUID usuarioId, Pageable pagina, String busqueda) {
    exigirCuenta(usuarioId, "CATEDRATICO");
    return referencias.estudiantes(pagina(pagina), busqueda(busqueda));
  }

  public Page<PuenteDisponibleDTO> puentesDisponibles(UUID usuarioId, Pageable pagina) {
    return puentesDisponibles(usuarioId, pagina, "");
  }

  public Page<PuenteDisponibleDTO> puentesDisponibles(
      UUID usuarioId, Pageable pagina, String busqueda) {
    exigirCuenta(usuarioId, "CATEDRATICO");
    return referencias.puentes(pagina(pagina), busqueda(busqueda));
  }

  private Page<AsignacionDTO> conPuentes(Page<Asignacion> resultado) {
    var puentes =
        referencias.datosPuentes(
            resultado.stream().map(Asignacion::getPuenteId).collect(Collectors.toSet()));
    return resultado.map(a -> mapper.aDTO(a, puentes.get(a.getPuenteId())));
  }

  private static String busqueda(String texto) {
    if (texto.length() > 120) {
      throw error(
          HttpStatus.BAD_REQUEST, "busqueda-invalida", "La búsqueda admite hasta 120 caracteres.");
    }
    return texto.strip();
  }

  private Curso propio(UUID usuarioId, UUID cursoId, boolean bloquear) {
    return (bloquear
            ? cursos.bloquearPropio(cursoId, usuarioId)
            : cursos.findByIdAndCatedraticoId(cursoId, usuarioId))
        .orElseThrow(
            () ->
                error(
                    HttpStatus.NOT_FOUND,
                    "curso-no-encontrado",
                    "El curso no existe o no le pertenece."));
  }

  private Inscripcion inscripcion(UUID cursoId, UUID inscripcionId) {
    return inscripciones
        .findByIdAndCursoId(inscripcionId, cursoId)
        .orElseThrow(
            () ->
                error(
                    HttpStatus.NOT_FOUND,
                    "inscripcion-no-encontrada",
                    "La inscripción no pertenece al curso."));
  }

  private void exigirCuenta(UUID id, String rol) {
    if (cuentas.rolSiEstaActiva(id).filter(r -> r == Rol.valueOf(rol)).isEmpty()) {
      throw error(
          HttpStatus.FORBIDDEN,
          "cuenta-no-habilitada",
          "Se requiere una cuenta activa con el rol correspondiente.");
    }
  }

  private static void exigirVigente(Curso curso) {
    if (!curso.isVigente()) {
      throw error(HttpStatus.CONFLICT, "curso-no-vigente", "El curso no está vigente.");
    }
  }

  private static Pageable pagina(Pageable pagina) {
    return PageRequest.of(
        pagina.getPageNumber(), Math.min(pagina.getPageSize(), 100), Sort.by("creadoEn", "id"));
  }

  private static NegocioException error(HttpStatus estado, String tipo, String detalle) {
    return new NegocioException(
        estado, tipo, "La operación de cursos no puede completarse", detalle);
  }
}
