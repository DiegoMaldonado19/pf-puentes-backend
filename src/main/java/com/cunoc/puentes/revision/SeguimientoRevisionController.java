package com.cunoc.puentes.revision;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Seguimiento académico")
@ApiResponse(responseCode = "401", description = "Sin sesión")
@ApiResponse(responseCode = "403", description = "Cuenta o rol no habilitado")
@ApiResponse(responseCode = "404", description = "Recurso ajeno o inexistente")
class SeguimientoRevisionController {
  private final SeguimientoRevisionService servicio;

  SeguimientoRevisionController(SeguimientoRevisionService servicio) {
    this.servicio = servicio;
  }

  @GetMapping("/revisiones/mis-pendientes")
  @PreAuthorize("hasRole('ESTUDIANTE')")
  @Operation(summary = "Observaciones emitidas y no resueltas del autor autenticado")
  @ApiResponse(responseCode = "200", description = "Página de pendientes propios")
  Pagina<SeguimientoRevisionRepository.Pendiente> pendientes(Principal actor, Pageable pagina) {
    return Pagina.de(servicio.pendientes(UUID.fromString(actor.getName()), pagina));
  }

  @PatchMapping("/observaciones/{id}/resolucion")
  @Operation(summary = "El autor marca una observación propia como resuelta en su borrador")
  @ApiResponse(responseCode = "200", description = "Resolución registrada o ya existente")
  @ApiResponse(responseCode = "409", description = "Inspección no editable")
  ObservacionRevisionDTO resolver(Principal actor, @PathVariable UUID id) {
    return servicio.resolver(UUID.fromString(actor.getName()), id);
  }

  @GetMapping("/revisiones/mis-inspecciones")
  @PreAuthorize("hasRole('ESTUDIANTE')")
  @Operation(summary = "Inspecciones propias con ciclos, aunque no queden pendientes")
  @ApiResponse(responseCode = "200", description = "Página de acceso al historial propio")
  Pagina<SeguimientoRevisionRepository.InspeccionRevisada> inspecciones(
      Principal actor, Pageable pagina) {
    return Pagina.de(servicio.inspecciones(UUID.fromString(actor.getName()), pagina));
  }

  @GetMapping("/inspecciones/{id}/revisiones")
  @Operation(summary = "Historial de ciclos autorizado, siempre sin calificaciones")
  @ApiResponse(responseCode = "200", description = "Página cronológica de ciclos")
  Pagina<HistorialRevisionDTO> historial(Principal actor, @PathVariable UUID id, Pageable pagina) {
    return Pagina.de(servicio.historial(UUID.fromString(actor.getName()), id, pagina));
  }

  @GetMapping("/revisiones/{id}/calificacion")
  @Operation(summary = "Calificación privada, solo autor o revisor de ese ciclo")
  @ApiResponse(responseCode = "200", description = "Calificación opcional")
  CalificacionRevisionDTO calificacion(Principal actor, @PathVariable UUID id) {
    return servicio.calificacion(UUID.fromString(actor.getName()), id);
  }

  @PutMapping("/revisiones/{id}/calificacion")
  @PreAuthorize("hasRole('CATEDRATICO')")
  @Operation(summary = "El revisor asignado registra o retira su calificación opcional")
  @ApiResponse(
      responseCode = "200",
      description = "Calificación guardada sin exponerla en auditoría")
  @ApiResponse(responseCode = "400", description = "Calificación fuera de rango o precisión")
  CalificacionRevisionDTO calificar(
      Principal actor, @PathVariable UUID id, @Valid @RequestBody CalificacionRevisionDTO datos) {
    return servicio.calificar(UUID.fromString(actor.getName()), id, datos);
  }

  record Pagina<T>(
      List<T> content,
      int number,
      int size,
      @JsonProperty("total_elements") long totalElements,
      @JsonProperty("total_pages") int totalPages) {
    static <T> Pagina<T> de(Page<T> pagina) {
      return new Pagina<>(
          pagina.getContent(),
          pagina.getNumber(),
          pagina.getSize(),
          pagina.getTotalElements(),
          pagina.getTotalPages());
    }
  }
}
