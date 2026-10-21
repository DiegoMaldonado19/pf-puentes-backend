package com.cunoc.puentes.usuario;

import com.cunoc.puentes.usuario.dto.CrearInvitacionDTO;
import com.cunoc.puentes.usuario.dto.InvitacionDTO;
import com.cunoc.puentes.usuario.dto.PaginaDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints del Administrador para invitar, listar y revocar (RN-USR-03). */
@RestController
@RequestMapping("/api/v1/invitaciones")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Tag(
    name = "Invitaciones",
    description = "Invitaciones a Profesional externo, Catedrático y Administrador")
@ApiResponse(responseCode = "401", description = "Sin sesión o con el token vencido")
@ApiResponse(responseCode = "403", description = "El rol no es Administrador")
class InvitacionController {

  private final InvitacionService invitaciones;

  InvitacionController(InvitacionService invitaciones) {
    this.invitaciones = invitaciones;
  }

  @Operation(summary = "Crea una invitación y envía el enlace al correo")
  @ApiResponse(responseCode = "201", description = "Invitación creada y auditada")
  @ApiResponse(
      responseCode = "409",
      description = "El correo ya tiene cuenta o una invitación vigente")
  @ApiResponse(responseCode = "422", description = "El rol no se invita (rol-no-invitable)")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  InvitacionDTO crear(Principal usuario, @Valid @RequestBody CrearInvitacionDTO datos) {
    return invitaciones.crear(UUID.fromString(usuario.getName()), datos);
  }

  @Operation(summary = "Lista las invitaciones, las más recientes primero")
  @ApiResponse(responseCode = "200", description = "Página de invitaciones")
  @GetMapping
  PaginaDTO<InvitacionDTO> listar(
      @RequestParam(required = false) EstadoInvitacion estado, Pageable pagina) {
    return invitaciones.listar(estado, pagina);
  }

  @Operation(summary = "Revoca una invitación abierta")
  @ApiResponse(responseCode = "200", description = "Invitación revocada y auditada")
  @ApiResponse(responseCode = "404", description = "No existe la invitación")
  @ApiResponse(
      responseCode = "409",
      description = "Ya fue aceptada o revocada (invitacion-cerrada)")
  @PostMapping("/{id}/revocacion")
  InvitacionDTO revocar(Principal usuario, @PathVariable UUID id) {
    return invitaciones.revocar(UUID.fromString(usuario.getName()), id);
  }
}
