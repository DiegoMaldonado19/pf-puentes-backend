package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.mantenimiento.dto.OrdenMantenimientoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/puentes/{puenteId}/ordenes-mantenimiento")
@Tag(name = "Mantenimiento", description = "Gestión de órdenes de mantenimiento")
class OrdenMantenimientoController {

  private final OrdenMantenimientoService servicio;

  OrdenMantenimientoController(OrdenMantenimientoService servicio) {
    this.servicio = servicio;
  }

  @PostMapping("/{ordenId}/aceptacion")
  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
  @Operation(
      summary = "Acepta una propuesta existente y la pasa a PROGRAMADA (RN-MTO-02/04)",
      description =
          "Sin cuerpo. Conserva prioridad, intervención y fecha programada existente, incluso null. "
              + "No genera propuestas ni modifica fechas. Registra el cambio en auditoría.")
  @ApiResponse(responseCode = "200", description = "Orden aceptada con su estado persistido")
  @ApiResponse(responseCode = "400", description = "UUID inválido")
  @ApiResponse(responseCode = "401", description = "Sin sesión o token inválido")
  @ApiResponse(
      responseCode = "403",
      description = "Solo Catedrático o Administrador acepta propuestas")
  @ApiResponse(responseCode = "404", description = "La orden no existe en el puente indicado")
  @ApiResponse(responseCode = "409", description = "La orden ya no está en PROPUESTA")
  @ApiResponse(
      responseCode = "422",
      description = "La inspección de origen pertenece a otro puente")
  OrdenMantenimientoDTO aceptar(@PathVariable UUID puenteId, @PathVariable UUID ordenId) {
    return servicio.aceptar(puenteId, ordenId);
  }
}
