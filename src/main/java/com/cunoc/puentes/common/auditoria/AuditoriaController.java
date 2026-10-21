package com.cunoc.puentes.common.auditoria;

import com.cunoc.puentes.common.auditoria.dto.PaginaAuditoriaDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de la bitácora (RN-USR-09). No hay alta, cambio ni borrado: la base tampoco lo permite.
 */
@RestController
@RequestMapping("/api/v1/auditoria")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Tag(name = "Bitácora", description = "Consulta de la auditoría de acciones sobre datos")
@ApiResponse(responseCode = "401", description = "Sin sesión o con el token vencido")
@ApiResponse(responseCode = "403", description = "El rol no es Administrador")
class AuditoriaController {

  private final ConsultaAuditoriaService consulta;

  AuditoriaController(ConsultaAuditoriaService consulta) {
    this.consulta = consulta;
  }

  @Operation(summary = "Lista las filas de auditoría con filtros opcionales")
  @ApiResponse(responseCode = "200", description = "Página de filas, ya saneadas")
  @ApiResponse(responseCode = "400", description = "desde es posterior a hasta")
  @GetMapping
  PaginaAuditoriaDTO listar(
      @RequestParam(required = false) String entidad,
      @RequestParam(name = "entidad_id", required = false) UUID entidadId,
      @RequestParam(name = "usuario_id", required = false) UUID usuarioId,
      @RequestParam(required = false) String accion,
      @RequestParam(required = false) Instant desde,
      @RequestParam(required = false) Instant hasta,
      Pageable pagina) {
    return consulta.listar(entidad, entidadId, usuarioId, accion, desde, hasta, pagina);
  }
}
