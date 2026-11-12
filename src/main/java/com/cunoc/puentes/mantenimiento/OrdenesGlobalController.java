package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.mantenimiento.dto.PaginaOrdenesGlobalDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ordenes-mantenimiento")
@Tag(name = "Mantenimiento", description = "Listado global de órdenes de mantenimiento (B9)")
public class OrdenesGlobalController {

  private final ConsultaOrdenesGlobalService consulta;

  public OrdenesGlobalController(ConsultaOrdenesGlobalService consulta) {
    this.consulta = consulta;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
  @Operation(
      summary = "Lista las órdenes de todos los puentes con filtros opcionales",
      description =
          "Filtros: estado, tipo, prioridad y q (busca en descripción, nombre y código del"
              + " puente). Orden: más recientes primero. size máximo 100.")
  @ApiResponse(responseCode = "200", description = "Página de órdenes con su puente de origen")
  @ApiResponse(responseCode = "400", description = "Paginación o filtros inválidos")
  @ApiResponse(responseCode = "401", description = "Sin sesión o token inválido")
  @ApiResponse(responseCode = "403", description = "Solo Catedrático o Administrador")
  PaginaOrdenesGlobalDTO listar(
      @RequestParam(required = false) EstadoOrden estado,
      @RequestParam(required = false) TipoOrden tipo,
      @RequestParam(required = false) Prioridad prioridad,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return consulta.listar(
        new ConsultaOrdenesGlobalService.FiltrosOrdenes(estado, tipo, prioridad, q), page, size);
  }
}
