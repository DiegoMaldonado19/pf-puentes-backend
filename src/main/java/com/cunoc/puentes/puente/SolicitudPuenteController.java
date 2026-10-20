package com.cunoc.puentes.puente;

import com.cunoc.puentes.puente.dto.AprobarSolicitudDTO;
import com.cunoc.puentes.puente.dto.CrearSolicitudPuenteDTO;
import com.cunoc.puentes.puente.dto.PaginaSolicitudesDTO;
import com.cunoc.puentes.puente.dto.RechazarSolicitudDTO;
import com.cunoc.puentes.puente.dto.SolicitudPuenteDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RN-INV-07. Fuera de /api/v1/puentes para que nunca quede bajo las rutas públicas. */
@RestController
@RequestMapping("/api/v1/solicitudes-puente")
@Tag(name = "Solicitudes de alta de puentes", description = "Catedrático solicita, Admin resuelve")
@ApiResponse(
    responseCode = "401",
    description = "Sin sesión",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(
    responseCode = "403",
    description = "Rol sin permiso",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
class SolicitudPuenteController {

  private static final String ADMINISTRADOR = "ROLE_ADMINISTRADOR";

  private final SolicitudPuenteService servicio;

  SolicitudPuenteController(SolicitudPuenteService servicio) {
    this.servicio = servicio;
  }

  @Operation(
      summary = "Solicita el alta de un puente",
      description =
          "Queda PENDIENTE hasta que un Administrador la resuelva. Si hay puentes a menos de 100 m"
              + " responde 409 con la lista; reenviar con confirmar_duplicado=true.")
  @ApiResponse(
      responseCode = "201",
      description = "Solicitud creada",
      content = @Content(schema = @Schema(implementation = SolicitudPuenteDTO.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Campos obligatorios faltantes o fuera de rango",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Posible duplicado (propiedad cercanos)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "Municipio inexistente o que no pertenece al departamento",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('CATEDRATICO')")
  ResponseEntity<SolicitudPuenteDTO> solicitar(
      @Valid @RequestBody CrearSolicitudPuenteDTO datos, Authentication usuario) {
    SolicitudPuenteDTO creada = servicio.solicitar(UUID.fromString(usuario.getName()), datos);
    return ResponseEntity.created(URI.create("/api/v1/solicitudes-puente/" + creada.id()))
        .body(creada);
  }

  @Operation(
      summary = "Lista solicitudes, las más recientes primero",
      description = "El Administrador ve todas; el Catedrático, solo las suyas.")
  @ApiResponse(
      responseCode = "200",
      description = "Página de solicitudes",
      content = @Content(schema = @Schema(implementation = PaginaSolicitudesDTO.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Paginación o estado inválidos",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')")
  PaginaSolicitudesDTO listar(
      @RequestParam(required = false) EstadoSolicitud estado,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication usuario) {
    return servicio.listar(
        UUID.fromString(usuario.getName()), esAdministrador(usuario), estado, page, size);
  }

  @Operation(summary = "Detalle de una solicitud")
  @ApiResponse(
      responseCode = "200",
      description = "Solicitud",
      content = @Content(schema = @Schema(implementation = SolicitudPuenteDTO.class)))
  @ApiResponse(
      responseCode = "404",
      description = "No existe o es de otro Catedrático",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')")
  SolicitudPuenteDTO detalle(@PathVariable UUID id, Authentication usuario) {
    return servicio.detalle(id, UUID.fromString(usuario.getName()), esAdministrador(usuario));
  }

  @Operation(
      summary = "Aprueba una solicitud y crea el puente",
      description =
          "Asigna el código GT igual que un alta directa. El año de construcción se completa"
              + " después editando el puente.")
  @ApiResponse(
      responseCode = "200",
      description = "Solicitud APROBADA con puente_id",
      content = @Content(schema = @Schema(implementation = SolicitudPuenteDTO.class)))
  @ApiResponse(
      responseCode = "404",
      description = "La solicitud no existe",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Ya resuelta, posible duplicado o correlativo agotado",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping(path = "/{id}/aprobacion", consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  SolicitudPuenteDTO aprobar(
      @PathVariable UUID id, @RequestBody AprobarSolicitudDTO datos, Authentication usuario) {
    return servicio.aprobar(id, UUID.fromString(usuario.getName()), datos);
  }

  @Operation(summary = "Rechaza una solicitud con motivo")
  @ApiResponse(
      responseCode = "200",
      description = "Solicitud RECHAZADA",
      content = @Content(schema = @Schema(implementation = SolicitudPuenteDTO.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Falta el motivo",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "La solicitud no existe",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Ya resuelta",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping(path = "/{id}/rechazo", consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  SolicitudPuenteDTO rechazar(
      @PathVariable UUID id,
      @Valid @RequestBody RechazarSolicitudDTO datos,
      Authentication usuario) {
    return servicio.rechazar(id, UUID.fromString(usuario.getName()), datos);
  }

  private static boolean esAdministrador(Authentication usuario) {
    return usuario.getAuthorities().stream().anyMatch(a -> ADMINISTRADOR.equals(a.getAuthority()));
  }
}
