package com.cunoc.puentes.usuario;

import com.cunoc.puentes.usuario.dto.ActualizarUsuarioDTO;
import com.cunoc.puentes.usuario.dto.CrearUsuarioDTO;
import com.cunoc.puentes.usuario.dto.MotivoDTO;
import com.cunoc.puentes.usuario.dto.PaginaDTO;
import com.cunoc.puentes.usuario.dto.UsuarioAdminDTO;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administración de cuentas (RN-USR-04, RN-USR-08). El perfil propio vive en {@link
 * UsuarioController}; Spring resuelve {@code /me} antes que {@code /{id}}.
 */
@RestController
@RequestMapping("/api/v1/usuarios")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Tag(
    name = "Administración de usuarios",
    description = "Verificación de colegiado, baja y reactivación")
@ApiResponse(responseCode = "401", description = "Sin sesión o con el token vencido")
@ApiResponse(responseCode = "403", description = "El rol no es Administrador")
class UsuarioAdminController {

  private final VerificacionColegiadoService colegiados;
  private final AdministracionUsuariosService cuentas;

  UsuarioAdminController(
      VerificacionColegiadoService colegiados, AdministracionUsuariosService cuentas) {
    this.colegiados = colegiados;
    this.cuentas = cuentas;
  }

  @Operation(summary = "Lista las cuentas con filtros opcionales de rol, estado y texto")
  @ApiResponse(responseCode = "200", description = "Página de cuentas, sin hash ni contadores")
  @ApiResponse(responseCode = "400", description = "La búsqueda supera 120 caracteres")
  @GetMapping
  PaginaDTO<UsuarioAdminDTO> listar(
      @RequestParam(required = false) Rol rol,
      @RequestParam(required = false) EstadoUsuario estado,
      @RequestParam(required = false) String q,
      Pageable pagina) {
    return cuentas.listar(rol, estado, q, pagina);
  }

  @Operation(summary = "Consulta una cuenta por id")
  @ApiResponse(responseCode = "200", description = "Datos de administración de la cuenta")
  @ApiResponse(responseCode = "404", description = "No existe la cuenta")
  @GetMapping("/{id}")
  UsuarioAdminDTO obtener(@PathVariable UUID id) {
    return cuentas.obtener(id);
  }

  @Operation(summary = "Aprueba el número de colegiado y activa al profesional externo")
  @ApiResponse(responseCode = "200", description = "Colegiado verificado; la cuenta queda ACTIVO")
  @ApiResponse(responseCode = "404", description = "No existe la cuenta")
  @ApiResponse(
      responseCode = "409",
      description = "No es un profesional pendiente (colegiado-no-verificable)")
  @PostMapping("/{id}/colegiado/verificacion")
  UsuarioAdminDTO aprobarColegiado(Principal usuario, @PathVariable UUID id) {
    return colegiados.aprobar(UUID.fromString(usuario.getName()), id);
  }

  @Operation(
      summary = "Rechaza el número de colegiado",
      description =
          "Da de baja la cuenta con el motivo; el número queda libre para otra invitación.")
  @ApiResponse(responseCode = "200", description = "Cuenta inactiva; el rechazo queda auditado")
  @ApiResponse(responseCode = "400", description = "Falta el motivo")
  @ApiResponse(responseCode = "404", description = "No existe la cuenta")
  @ApiResponse(
      responseCode = "409",
      description = "No es un profesional pendiente (colegiado-no-verificable)")
  @PostMapping("/{id}/colegiado/rechazo")
  UsuarioAdminDTO rechazarColegiado(
      Principal usuario, @PathVariable UUID id, @Valid @RequestBody MotivoDTO datos) {
    return colegiados.rechazar(UUID.fromString(usuario.getName()), id, datos.motivo());
  }

  @Operation(summary = "Da de baja lógica una cuenta y revoca sus sesiones")
  @ApiResponse(responseCode = "200", description = "Cuenta inactiva y sesiones revocadas")
  @ApiResponse(responseCode = "400", description = "Falta el motivo")
  @ApiResponse(responseCode = "404", description = "No existe la cuenta")
  @ApiResponse(responseCode = "409", description = "Ya inactiva o es el último administrador")
  @ApiResponse(
      responseCode = "422",
      description = "El administrador no puede darse de baja a sí mismo")
  @PostMapping("/{id}/baja")
  UsuarioAdminDTO darDeBaja(
      Principal usuario, @PathVariable UUID id, @Valid @RequestBody MotivoDTO datos) {
    return cuentas.darDeBaja(UUID.fromString(usuario.getName()), id, datos.motivo());
  }

  @Operation(summary = "Reactiva una cuenta dada de baja")
  @ApiResponse(responseCode = "200", description = "Cuenta reactivada (ACTIVO o PENDIENTE)")
  @ApiResponse(responseCode = "404", description = "No existe la cuenta")
  @ApiResponse(responseCode = "409", description = "No está inactiva o el colegiado está duplicado")
  @PostMapping("/{id}/reactivacion")
  UsuarioAdminDTO reactivar(@PathVariable UUID id) {
    return cuentas.reactivar(id);
  }

  @Operation(
      summary = "Activa una cuenta pendiente con el correo verificado",
      description = "Idempotente: una cuenta ya activa responde 200 sin cambios (RN-USR-02).")
  @ApiResponse(responseCode = "200", description = "Cuenta activa")
  @ApiResponse(responseCode = "404", description = "No existe la cuenta")
  @ApiResponse(responseCode = "409", description = "La cuenta está dada de baja")
  @ApiResponse(
      responseCode = "422",
      description = "Falta verificar el correo o, si es profesional, el colegiado")
  @PostMapping("/{id}/activacion")
  UsuarioAdminDTO activar(@PathVariable UUID id) {
    return cuentas.activar(id);
  }

  @Operation(
      summary = "Da de alta una cuenta y envía el enlace para fijar su contraseña",
      description =
          "La cuenta queda activa con el correo verificado. La contraseña provisional es"
              + " inutilizable: el enlace por correo es la única vía de entrada (B5).")
  @ApiResponse(responseCode = "201", description = "Cuenta creada")
  @ApiResponse(responseCode = "409", description = "Ya existe una cuenta con ese correo")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  UsuarioAdminDTO crear(@Valid @RequestBody CrearUsuarioDTO datos) {
    return cuentas.crear(datos);
  }

  @Operation(
      summary = "Edita nombre, correo o rol de una cuenta",
      description =
          "El rol solo cambia entre CATEDRATICO y ADMINISTRADOR; nadie cambia su propio rol ni"
              + " se quita el rol al último administrador.")
  @ApiResponse(responseCode = "200", description = "Cuenta actualizada")
  @ApiResponse(responseCode = "409", description = "Correo en uso o último administrador")
  @ApiResponse(responseCode = "422", description = "Cambio de rol no permitido o rol propio")
  @PutMapping("/{id}")
  UsuarioAdminDTO actualizar(
      Principal usuario, @PathVariable UUID id, @Valid @RequestBody ActualizarUsuarioDTO datos) {
    return cuentas.actualizar(UUID.fromString(usuario.getName()), id, datos);
  }

  @Operation(summary = "Quita el bloqueo por intentos fallidos")
  @ApiResponse(responseCode = "200", description = "Cuenta desbloqueada")
  @PostMapping("/{id}/desbloqueo")
  UsuarioAdminDTO desbloquear(@PathVariable UUID id) {
    return cuentas.desbloquear(id);
  }

  @Operation(
      summary = "Reenvía el enlace de verificación",
      description = "Solo para cuentas pendientes con el correo sin verificar.")
  @ApiResponse(responseCode = "202", description = "Enlace reenviado")
  @ApiResponse(responseCode = "409", description = "No hay verificación pendiente")
  @PostMapping("/{id}/reenvio-verificacion")
  @ResponseStatus(HttpStatus.ACCEPTED)
  void reenviarVerificacion(@PathVariable UUID id) {
    cuentas.reenviarVerificacion(id);
  }
}
