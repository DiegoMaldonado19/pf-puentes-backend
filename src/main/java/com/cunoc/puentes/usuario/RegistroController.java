package com.cunoc.puentes.usuario;

import com.cunoc.puentes.usuario.dto.CuentaRegistradaDTO;
import com.cunoc.puentes.usuario.dto.ReenviarVerificacionDTO;
import com.cunoc.puentes.usuario.dto.RegistrarEstudianteDTO;
import com.cunoc.puentes.usuario.dto.VerificarCorreoDTO;
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

/** Endpoints públicos (RN-USR-10): se declaran en {@code sgp.seguridad.rutas-publicas}. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Registro", description = "Registro de estudiantes y verificación del correo")
@ApiResponse(responseCode = "400", description = "Falta un campo o su formato no es válido")
class RegistroController {

  private final RegistroService registro;
  private final VerificacionCorreoService verificacion;

  RegistroController(RegistroService registro, VerificacionCorreoService verificacion) {
    this.registro = registro;
    this.verificacion = verificacion;
  }

  @Operation(
      summary = "Registra un estudiante",
      description =
          "Crea la cuenta PENDIENTE con el correo sin verificar y envía el enlace de verificación."
              + " El rol siempre es ESTUDIANTE (RN-USR-02).")
  @ApiResponse(responseCode = "201", description = "Cuenta creada")
  @ApiResponse(responseCode = "409", description = "Ya existe una cuenta con ese correo")
  @PostMapping("/registro")
  @ResponseStatus(HttpStatus.CREATED)
  CuentaRegistradaDTO registrar(@Valid @RequestBody RegistrarEstudianteDTO datos) {
    return registro.registrar(datos);
  }

  @Operation(
      summary = "Verifica el correo con el token del enlace",
      description = "No activa la cuenta: la activación es del catedrático (RN-USR-02).")
  @ApiResponse(responseCode = "204", description = "Correo verificado")
  @ApiResponse(
      responseCode = "422",
      description = "El token venció, ya se usó o no existe (token-invalido)")
  @PostMapping("/verificar-correo")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void verificarCorreo(@Valid @RequestBody VerificarCorreoDTO datos) {
    verificacion.verificar(datos.token());
  }

  @Operation(
      summary = "Reenvía el enlace de verificación",
      description =
          "Anula el enlace anterior. Responde igual exista o no la cuenta, para no revelarlo.")
  @ApiResponse(responseCode = "202", description = "Solicitud recibida")
  @PostMapping("/reenviar-verificacion")
  @ResponseStatus(HttpStatus.ACCEPTED)
  void reenviarVerificacion(@Valid @RequestBody ReenviarVerificacionDTO datos) {
    verificacion.reenviar(datos.correo());
  }
}
