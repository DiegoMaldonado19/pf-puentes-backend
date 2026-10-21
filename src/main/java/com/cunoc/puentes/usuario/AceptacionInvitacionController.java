package com.cunoc.puentes.usuario;

import com.cunoc.puentes.usuario.dto.AceptarInvitacionDTO;
import com.cunoc.puentes.usuario.dto.CuentaRegistradaDTO;
import com.cunoc.puentes.usuario.dto.InvitacionPublicaDTO;
import com.cunoc.puentes.usuario.dto.TokenInvitacionDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints públicos del enlace de invitación (RN-USR-10): se declaran en {@code
 * sgp.seguridad.rutas-publicas}. El token viaja en el cuerpo para no quedar en bitácoras de acceso.
 */
@RestController
@RequestMapping("/api/v1/auth/invitaciones")
@Tag(
    name = "Aceptación de invitaciones",
    description = "Consulta y aceptación del enlace de invitación")
@ApiResponse(responseCode = "400", description = "Falta un campo o su formato no es válido")
class AceptacionInvitacionController {

  private final InvitacionService invitaciones;

  AceptacionInvitacionController(InvitacionService invitaciones) {
    this.invitaciones = invitaciones;
  }

  @Operation(summary = "Consulta el correo y el rol de un enlace de invitación")
  @ApiResponse(responseCode = "200", description = "Invitación vigente")
  @ApiResponse(
      responseCode = "422",
      description = "El enlace venció, se usó, se revocó o no existe")
  @PostMapping("/consultar")
  InvitacionPublicaDTO consultar(@Valid @RequestBody TokenInvitacionDTO datos) {
    return invitaciones.consultar(datos.token());
  }

  @Operation(summary = "Acepta la invitación y crea la cuenta")
  @ApiResponse(responseCode = "201", description = "Cuenta creada")
  @ApiResponse(responseCode = "409", description = "El correo o el colegiado ya están en uso")
  @ApiResponse(responseCode = "422", description = "El enlace no es válido o falta el colegiado")
  @PostMapping("/aceptar")
  @ResponseStatus(HttpStatus.CREATED)
  CuentaRegistradaDTO aceptar(@Valid @RequestBody AceptarInvitacionDTO datos) {
    return invitaciones.aceptar(datos);
  }
}
