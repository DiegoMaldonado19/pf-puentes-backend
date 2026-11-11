package com.cunoc.puentes.procesos;

import com.cunoc.puentes.procesos.dto.EjecucionProcesoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/procesos")
@Tag(name = "Procesos", description = "Disparo manual de procesos programados (B10)")
public class ProcesosController {

  private final EjecucionManualProcesosService servicio;

  public ProcesosController(EjecucionManualProcesosService servicio) {
    this.servicio = servicio;
  }

  @PostMapping("/{nombre}/ejecucion")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @ApiResponse(responseCode = "200", description = "Proceso ejecutado")
  @ApiResponse(responseCode = "404", description = "Proceso manual desconocido")
  @ApiResponse(responseCode = "409", description = "El proceso ya se está ejecutando")
  @ApiResponse(responseCode = "403", description = "Solo administrador")
  @ApiResponse(responseCode = "401", description = "Sin sesión")
  @Operation(
      summary = "Ejecuta ahora un proceso programado, respetando el bloqueo de ShedLock",
      description = "Auditado. Solo administrador.")
  public ResponseEntity<EjecucionProcesoDTO> ejecutar(@PathVariable String nombre) {
    return ResponseEntity.ok(servicio.ejecutar(nombre));
  }
}
