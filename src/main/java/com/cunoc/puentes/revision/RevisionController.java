package com.cunoc.puentes.revision;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('CATEDRATICO')")
@Tag(name = "Revisión académica")
@ApiResponse(responseCode = "401", description = "Sin sesión")
@ApiResponse(responseCode = "403", description = "No es catedrático activo")
class RevisionController {
  private final RevisionService servicio;

  RevisionController(RevisionService servicio) {
    this.servicio = servicio;
  }

  @GetMapping("/revisiones/pendientes")
  @Operation(summary = "Bandeja paginada de estudiantes de cursos propios")
  @ApiResponse(responseCode = "200", description = "Página sin calificaciones ni respuestas")
  @ApiResponse(responseCode = "400", description = "Estado de filtro inválido")
  Pagina pendientes(
      Principal usuario, @RequestParam(required = false) String estado, Pageable pagina) {
    Page<BandejaRevisionDTO> resultado =
        servicio.pendientes(UUID.fromString(usuario.getName()), estado, pagina);
    return new Pagina(
        resultado.getContent(),
        resultado.getNumber(),
        resultado.getSize(),
        resultado.getTotalElements(),
        resultado.getTotalPages());
  }

  @PostMapping("/inspecciones/{inspeccionId}/revisiones")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Abre un ciclo y transita a EN_REVISION de forma atómica")
  @ApiResponse(responseCode = "201", description = "Ciclo creado y auditado")
  @ApiResponse(responseCode = "404", description = "Inspección ajena, propia o inexistente")
  @ApiResponse(responseCode = "409", description = "Estado incompatible o ciclo ya abierto")
  RevisionAbiertaDTO abrir(Principal usuario, @PathVariable UUID inspeccionId) {
    return servicio.abrir(UUID.fromString(usuario.getName()), inspeccionId);
  }

  record Pagina(
      List<BandejaRevisionDTO> content,
      int number,
      int size,
      @JsonProperty("total_elements") long totalElements,
      @JsonProperty("total_pages") int totalPages) {}
}
