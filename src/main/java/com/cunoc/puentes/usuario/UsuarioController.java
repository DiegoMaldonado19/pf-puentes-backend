package com.cunoc.puentes.usuario;

import com.cunoc.puentes.usuario.dto.PerfilDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios")
@PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO', 'ADMINISTRADOR')")
@Tag(name = "Usuarios", description = "Cuentas de usuario")
@ApiResponse(responseCode = "401", description = "Sin sesión o con el token vencido")
class UsuarioController {

  private final PerfilService perfiles;

  UsuarioController(PerfilService perfiles) {
    this.perfiles = perfiles;
  }

  @Operation(summary = "Consulta los datos de la cuenta autenticada")
  @ApiResponse(responseCode = "200", description = "Id, nombre, correo, rol y estado")
  @GetMapping("/me")
  PerfilDTO perfilPropio(Principal usuario) {
    // Contrato 14: el nombre de la autenticación es el UUID del usuario
    return perfiles.obtener(UUID.fromString(usuario.getName()));
  }
}
