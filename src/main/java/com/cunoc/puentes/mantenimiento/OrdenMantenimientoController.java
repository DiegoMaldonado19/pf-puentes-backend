package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.mantenimiento.dto.OrdenMantenimientoDTO;
import com.cunoc.puentes.mantenimiento.dto.PaginaOrdenesDTO;
import com.cunoc.puentes.mantenimiento.dto.PaginacionOrdenesDTO;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/puentes/{puenteId}/ordenes-mantenimiento")
@Tag(name = "Mantenimiento", description = "Gestión de órdenes de mantenimiento")
class OrdenMantenimientoController {

  private final OrdenMantenimientoService servicio;

  private final ConsultaPropuestasService consulta;
  private final ConsultaOrdenesService historial;

  OrdenMantenimientoController(
      OrdenMantenimientoService servicio,
      ConsultaPropuestasService consulta,
      ConsultaOrdenesService historial) {
    this.servicio = servicio;
    this.consulta = consulta;
    this.historial = historial;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
  @Operation(
      summary = "Lista el historial de órdenes del puente, incluidos estados terminales",
      description =
          "Lectores confirmados: Catedrático y Administrador. Orden por ID descendente; "
              + "page desde 0; size por defecto 20 y máximo 100. No modifica ni elimina órdenes.")
  @ApiResponse(
      responseCode = "200",
      description = "Página de órdenes con puente de origen y totales")
  @ApiResponse(responseCode = "400", description = "UUID o paginación inválidos")
  @ApiResponse(responseCode = "401", description = "Sin sesión o token inválido")
  @ApiResponse(
      responseCode = "403",
      description = "Solo Catedrático o Administrador consulta órdenes")
  @ApiResponse(responseCode = "404", description = "Puente inexistente")
  PaginaOrdenesDTO listar(
      @PathVariable UUID puenteId,
      @Valid @ModelAttribute @ParameterObject PaginacionOrdenesDTO pagina) {
    return historial.listar(puenteId, pagina.page(), pagina.size());
  }

  @GetMapping("/{ordenId}")
  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
  @Operation(
      summary = "Consulta el detalle de una orden del puente indicado",
      description =
          "Incluye origen, prioridades, fechas y motivo disponibles. "
              + "Una orden de otro puente devuelve 404; consultar no habilita acciones sobre ella.")
  @ApiResponse(responseCode = "200", description = "DTO de la orden, también si es terminal")
  @ApiResponse(responseCode = "400", description = "UUID inválido")
  @ApiResponse(responseCode = "401", description = "Sin sesión o token inválido")
  @ApiResponse(
      responseCode = "403",
      description = "Solo Catedrático o Administrador consulta órdenes")
  @ApiResponse(responseCode = "404", description = "La orden no existe en el puente indicado")
  OrdenMantenimientoDTO obtener(@PathVariable UUID puenteId, @PathVariable UUID ordenId) {
    return historial.obtener(puenteId, ordenId);
  }

  @GetMapping("/propuestas")
  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
  @Operation(
      summary = "Lista propuestas pendientes del puente para su aceptación",
      description =
          "Solo PROPUESTA, por ID descendente. page desde 0; size por defecto 20, máximo 100. "
              + "Lectores confirmados por el equipo: Catedrático y Administrador. No expone el historial.")
  @ApiResponse(
      responseCode = "200",
      description = "Página de propuestas, con puente de origen y totales filtrados")
  @ApiResponse(responseCode = "400", description = "UUID o paginación inválidos")
  @ApiResponse(responseCode = "401", description = "Sin sesión o token inválido")
  @ApiResponse(
      responseCode = "403",
      description = "Solo Catedrático o Administrador consulta esta bandeja")
  @ApiResponse(responseCode = "404", description = "Puente inexistente")
  PaginaOrdenesDTO listarPropuestas(
      @PathVariable UUID puenteId,
      @Valid @ModelAttribute @ParameterObject PaginacionOrdenesDTO pagina) {
    return consulta.listar(puenteId, pagina.page(), pagina.size());
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

  @PostMapping("/{ordenId}/inicio-ejecucion")
  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
  @Operation(
      summary = "Inicia una orden PROGRAMADA y la pasa a EN_EJECUCION (RN-MTO-04)",
      description =
          "Sin cuerpo. Actores confirmados: Catedrático y Administrador. "
              + "Conserva fechas, origen, prioridades e intervención. Audita el cambio en la misma transacción. "
              + "No admite un estado de destino enviado por el cliente.")
  @ApiResponse(responseCode = "200", description = "Orden persistida en EN_EJECUCION")
  @ApiResponse(responseCode = "400", description = "UUID inválido")
  @ApiResponse(responseCode = "401", description = "Sin sesión o token inválido")
  @ApiResponse(
      responseCode = "403",
      description = "Solo Catedrático o Administrador inicia ejecución")
  @ApiResponse(responseCode = "404", description = "La orden no existe en el puente indicado")
  @ApiResponse(responseCode = "409", description = "La orden ya no está en PROGRAMADA")
  @ApiResponse(
      responseCode = "422",
      description = "La inspección de origen pertenece a otro puente")
  OrdenMantenimientoDTO iniciarEjecucion(@PathVariable UUID puenteId, @PathVariable UUID ordenId) {
    return servicio.iniciarEjecucion(puenteId, ordenId);
  }
}
