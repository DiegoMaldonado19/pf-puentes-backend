package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.mantenimiento.dto.PaginacionOrdenesDTO;
import com.cunoc.puentes.mantenimiento.dto.SeguimientoRutinarioDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/puentes/{puenteId}/ordenes-mantenimiento/rutinario-anual")
@Tag(name = "Mantenimiento")
class SeguimientoRutinarioController {
  private final SeguimientoRutinarioService servicio;

  SeguimientoRutinarioController(SeguimientoRutinarioService servicio) {
    this.servicio = servicio;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
  @Operation(
      summary = "Consulta la programación rutinaria anual (RN-MTO-07)",
      description =
          "Referencia acordada: última ejecución RUTINARIO o registro inicial sin ejecuciones. "
              + "Límite: un año de calendario. Programación en plazo: PROGRAMADA/EN_EJECUCION con fecha "
              + "entre hoy y el límite inclusive. Conteos globales y página de abiertas por ID descendente. "
              + "Fecha pasada y fecha posterior al límite son indicadores independientes, no se suman como total. "
              + "No crea órdenes, modifica fechas ni certifica ejecución. page=0, size=20, máximo 100.")
  @ApiResponse(responseCode = "200", description = "Resumen y página de rutinarias abiertas")
  @ApiResponse(responseCode = "400", description = "UUID o paginación inválidos")
  @ApiResponse(responseCode = "401", description = "Sin sesión")
  @ApiResponse(responseCode = "403", description = "Solo Catedrático/Admin")
  @ApiResponse(responseCode = "404", description = "Puente inexistente")
  SeguimientoRutinarioDTO consultar(
      @PathVariable UUID puenteId,
      @Valid @ModelAttribute @ParameterObject PaginacionOrdenesDTO pagina) {
    return servicio.consultar(puenteId, pagina.page(), pagina.size());
  }
}
