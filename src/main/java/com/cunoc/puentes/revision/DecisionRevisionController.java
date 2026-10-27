package com.cunoc.puentes.revision;

import com.cunoc.puentes.archivo.dto.ArchivoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/revisiones/{revisionId}")
@PreAuthorize("hasRole('CATEDRATICO')")
@Tag(name = "Revisión académica")
@ApiResponse(responseCode = "401", description = "Sin sesión")
@ApiResponse(responseCode = "403", description = "Cuenta no habilitada")
@ApiResponse(responseCode = "404", description = "Revisión ajena o inexistente")
class DecisionRevisionController {
  private final DecisionRevisionService servicio;

  DecisionRevisionController(DecisionRevisionService servicio) {
    this.servicio = servicio;
  }

  @GetMapping
  @Operation(summary = "Consulta el ciclo y su formulario original sin calificación")
  @ApiResponse(responseCode = "200", description = "Detalle autorizado")
  DetalleRevisionDTO detalle(Principal actor, @PathVariable UUID revisionId) {
    return servicio.detalle(UUID.fromString(actor.getName()), revisionId);
  }

  @PostMapping("/veredicto")
  @Operation(summary = "Emite un veredicto y delega publicación o retorno a borrador")
  @ApiResponse(responseCode = "200", description = "Decisión confirmada junto con su transición")
  @ApiResponse(responseCode = "400", description = "Cuerpo inválido")
  @ApiResponse(responseCode = "409", description = "Ciclo cerrado o estado incompatible")
  @ApiResponse(
      responseCode = "422",
      description = "Observación o justificación requeridas, o cálculo no disponible")
  DetalleRevisionDTO emitir(
      Principal actor,
      @PathVariable UUID revisionId,
      @Valid @RequestBody DecisionRevisionDTO datos) {
    return servicio.emitir(UUID.fromString(actor.getName()), revisionId, datos);
  }

  @GetMapping("/observaciones")
  @Operation(summary = "Lista observaciones ancladas del ciclo propio")
  @ApiResponse(responseCode = "200", description = "Observaciones del ciclo")
  List<ObservacionRevisionDTO> listar(Principal actor, @PathVariable UUID revisionId) {
    return servicio.listar(UUID.fromString(actor.getName()), revisionId);
  }

  @GetMapping("/archivos")
  @Operation(summary = "Lista fotos y documentos de la inspección con URLs firmadas")
  @ApiResponse(responseCode = "200", description = "Archivos autorizados para la revisión")
  List<ArchivoDTO> archivos(Principal actor, @PathVariable UUID revisionId) {
    return servicio.archivos(UUID.fromString(actor.getName()), revisionId);
  }

  @PostMapping("/observaciones")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Ancla una observación a un campo de la versión original")
  @ApiResponse(responseCode = "201", description = "Observación creada y auditada")
  @ApiResponse(responseCode = "400", description = "Cuerpo inválido")
  @ApiResponse(responseCode = "409", description = "Ciclo cerrado")
  @ApiResponse(responseCode = "422", description = "Referencia inexistente o texto inválido")
  ObservacionRevisionDTO agregar(
      Principal actor,
      @PathVariable UUID revisionId,
      @Valid @RequestBody AgregarObservacionDTO datos) {
    return servicio.agregar(UUID.fromString(actor.getName()), revisionId, datos);
  }
}
