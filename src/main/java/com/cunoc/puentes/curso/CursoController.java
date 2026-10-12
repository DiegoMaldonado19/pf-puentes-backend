package com.cunoc.puentes.curso;

import com.cunoc.puentes.curso.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('CATEDRATICO')")
@Tag(name = "Cursos", description = "Cursos propios, inscripciones y asignaciones (P7)")
@ApiResponse(responseCode = "400", description = "DTO, UUID o búsqueda inválidos")
@ApiResponse(responseCode = "401", description = "Sin autenticación")
@ApiResponse(responseCode = "403", description = "Rol o cuenta no habilitados")
@ApiResponse(responseCode = "404", description = "Recurso inexistente o ajeno")
class CursoController {
  private final CursoService servicio;

  CursoController(CursoService servicio) {
    this.servicio = servicio;
  }

  @GetMapping("/cursos")
  @Operation(summary = "Lista los cursos del catedrático autenticado")
  @ApiResponse(responseCode = "200", description = "Página de cursos propios")
  PaginaDTO<CursoDTO> listar(Principal usuario, Pageable pagina) {
    return PaginaDTO.de(servicio.listar(id(usuario), pagina));
  }

  @PostMapping("/cursos")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Crea un curso del catedrático autenticado")
  @ApiResponse(responseCode = "201", description = "Curso creado y auditado")
  CursoDTO crear(Principal usuario, @Valid @RequestBody GuardarCursoDTO datos) {
    return servicio.crear(id(usuario), datos);
  }

  @GetMapping("/cursos/{cursoId}")
  @Operation(summary = "Consulta un curso propio")
  @ApiResponse(responseCode = "200", description = "Detalle del curso")
  CursoDTO obtener(Principal usuario, @PathVariable UUID cursoId) {
    return servicio.obtener(id(usuario), cursoId);
  }

  @PatchMapping("/cursos/{cursoId}")
  @Operation(
      summary = "Actualiza datos y vigencia de un curso propio",
      description = "Enviar nombre, anio, semestre y vigente; no cambia el responsable.")
  @ApiResponse(responseCode = "200", description = "Curso actualizado y auditado")
  CursoDTO actualizar(
      Principal usuario, @PathVariable UUID cursoId, @Valid @RequestBody GuardarCursoDTO datos) {
    return servicio.actualizar(id(usuario), cursoId, datos);
  }

  @GetMapping("/cursos/{cursoId}/inscripciones")
  @Operation(summary = "Lista inscripciones de un curso propio")
  @ApiResponse(responseCode = "200", description = "Página de inscripciones")
  PaginaDTO<InscripcionDTO> inscripciones(
      Principal usuario, @PathVariable UUID cursoId, Pageable pagina) {
    return PaginaDTO.de(servicio.listarInscripciones(id(usuario), cursoId, pagina));
  }

  @PostMapping("/cursos/{cursoId}/inscripciones")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Inscribe un estudiante y solicita activación a P2 cuando está pendiente")
  @ApiResponse(responseCode = "201", description = "Inscripción creada y auditada")
  @ApiResponse(responseCode = "409", description = "Inscripción duplicada o curso no vigente")
  @ApiResponse(responseCode = "422", description = "No es estudiante elegible")
  @ApiResponse(
      responseCode = "503",
      description = "Activación de P2 no integrada; no se crea inscripción")
  InscripcionDTO inscribir(
      Principal usuario,
      @PathVariable UUID cursoId,
      @Valid @RequestBody InscribirEstudianteDTO datos) {
    return servicio.inscribir(id(usuario), cursoId, datos);
  }

  @GetMapping("/cursos/{cursoId}/inscripciones/{inscripcionId}/asignaciones")
  @Operation(summary = "Lista puentes asignados a una inscripción del curso propio")
  @ApiResponse(responseCode = "200", description = "Página de asignaciones")
  PaginaDTO<AsignacionDTO> asignaciones(
      Principal usuario,
      @PathVariable UUID cursoId,
      @PathVariable UUID inscripcionId,
      Pageable pagina) {
    return PaginaDTO.de(servicio.listarAsignaciones(id(usuario), cursoId, inscripcionId, pagina));
  }

  @PostMapping("/cursos/{cursoId}/inscripciones/{inscripcionId}/asignaciones")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Asigna un puente activo a un estudiante del curso propio")
  @ApiResponse(responseCode = "201", description = "Asignación creada y auditada")
  @ApiResponse(responseCode = "409", description = "Asignación duplicada o curso no vigente")
  @ApiResponse(responseCode = "422", description = "Puente no disponible")
  AsignacionDTO asignar(
      Principal usuario,
      @PathVariable UUID cursoId,
      @PathVariable UUID inscripcionId,
      @Valid @RequestBody AsignarPuenteDTO datos) {
    return servicio.asignar(id(usuario), cursoId, inscripcionId, datos);
  }

  @GetMapping("/mis-asignaciones")
  @PreAuthorize("hasRole('ESTUDIANTE')")
  @Operation(
      summary = "Consulta las asignaciones propias vigentes para preparar la salida (contrato 6)")
  @ApiResponse(
      responseCode = "200",
      description = "Página de asignaciones vigentes de puentes activos")
  PaginaDTO<AsignacionDTO> propias(Principal usuario, Pageable pagina) {
    return PaginaDTO.de(servicio.consultarAsignacionesVigentes(id(usuario), pagina));
  }

  @GetMapping("/cursos/estudiantes-elegibles")
  @Operation(
      summary = "Lista estudiantes no inactivos con correo verificado; sin correo ni credenciales")
  @ApiResponse(responseCode = "200", description = "Página de estudiantes elegibles")
  PaginaDTO<EstudianteElegibleDTO> estudiantes(
      Principal usuario, Pageable pagina, @RequestParam(defaultValue = "") String q) {
    return PaginaDTO.de(servicio.estudiantesElegibles(id(usuario), pagina, q));
  }

  @GetMapping("/cursos/puentes-disponibles")
  @Operation(summary = "Lista puentes activos para asignación")
  @ApiResponse(responseCode = "200", description = "Página de puentes activos")
  PaginaDTO<PuenteDisponibleDTO> puentes(
      Principal usuario, Pageable pagina, @RequestParam(defaultValue = "") String q) {
    return PaginaDTO.de(servicio.puentesDisponibles(id(usuario), pagina, q));
  }

  private static UUID id(Principal usuario) {
    return UUID.fromString(usuario.getName());
  }
}
