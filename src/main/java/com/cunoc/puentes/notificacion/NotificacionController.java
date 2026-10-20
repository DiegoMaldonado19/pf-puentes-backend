package com.cunoc.puentes.notificacion;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.notificacion.dto.ConteoNoLeidasDTO;
import com.cunoc.puentes.notificacion.dto.NotificacionDTO;
import com.cunoc.puentes.notificacion.dto.PaginaNotificacionesDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notificaciones")
@PreAuthorize("isAuthenticated()")
@Tag(
    name = "Notificaciones",
    description = "Avisos y alertas para usuarios dentro de la aplicación")
@ApiResponse(
    responseCode = "401",
    description = "Sin sesión o token vencido",
    content =
        @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class),
            examples =
                @ExampleObject(
                    summary = "Sesion requerida",
                    value =
                        """
            {
              "type": "https://sgp.cunoc.usac.edu.gt/errores/no-autenticado",
              "title": "Autenticación requerida",
              "status": 401,
              "detail": "Falta una sesión válida para esta operación.",
              "instance": "/api/v1/notificaciones"
            }
            """)))
@ApiResponse(
    responseCode = "403",
    description = "Acceso denegado",
    content =
        @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class),
            examples =
                @ExampleObject(
                    summary = "Acceso denegado",
                    value =
                        """
            {
              "type": "https://sgp.cunoc.usac.edu.gt/errores/acceso-denegado",
              "title": "Acceso denegado",
              "status": 403,
              "detail": "No tiene permiso para esta operación.",
              "instance": "/api/v1/notificaciones"
            }
            """)))
class NotificacionController {

  private final NotificacionService servicio;

  NotificacionController(NotificacionService servicio) {
    this.servicio = servicio;
  }

  @Operation(
      summary = "Lista las notificaciones del usuario autenticado",
      description = "Devuelve los avisos paginados en orden descendente estable por creadoEn/id.")
  @ApiResponse(
      responseCode = "200",
      description = "Página de notificaciones",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = PaginaNotificacionesDTO.class)))
  @ApiResponse(
      responseCode = "400",
      description =
          "Parámetros de paginación inválidos, desbordamiento de desplazamiento o usuario malformado",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = ProblemDetail.class),
              examples =
                  @ExampleObject(
                      summary = "Paginacion invalida",
                      value =
                          """
            {
              "type": "https://sgp.cunoc.usac.edu.gt/errores/paginacion-invalida",
              "title": "Paginación inválida",
              "status": 400,
              "detail": "Los parámetros de paginación deben cumplir page >= 0 y 1 <= size <= 100.",
              "instance": "/api/v1/notificaciones"
            }
            """)))
  @GetMapping
  PaginaNotificacionesDTO listar(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Principal usuario) {
    return servicio.listar(extraerUsuarioId(usuario), page, size);
  }

  @Operation(
      summary = "Conteo de notificaciones no leídas",
      description = "Devuelve el número total de avisos no leídos del usuario autenticado.")
  @ApiResponse(
      responseCode = "200",
      description = "Total de no leídas",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ConteoNoLeidasDTO.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Identificador del usuario autenticado malformado (no es UUID)",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = ProblemDetail.class),
              examples =
                  @ExampleObject(
                      summary = "Identificador invalido",
                      value =
                          """
            {
              "type": "https://sgp.cunoc.usac.edu.gt/errores/identificador-usuario-invalido",
              "title": "Identificador de usuario inválido",
              "status": 400,
              "detail": "El identificador del usuario autenticado no tiene formato UUID válido.",
              "instance": "/api/v1/notificaciones/no-leidas/conteo"
            }
            """)))
  @GetMapping("/no-leidas/conteo")
  ConteoNoLeidasDTO contarNoLeidas(Principal usuario) {
    return servicio.contarNoLeidas(extraerUsuarioId(usuario));
  }

  @Operation(
      summary = "Marca una notificación como leída",
      description =
          "Marca el aviso como leído conservando la primera fecha de lectura en repeticiones.")
  @ApiResponse(
      responseCode = "200",
      description = "Notificación actualizada",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = NotificacionDTO.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Identificador inválido en ruta o usuario autenticado malformado",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = ProblemDetail.class),
              examples =
                  @ExampleObject(
                      summary = "Identificador invalido",
                      value =
                          """
            {
              "type": "https://sgp.cunoc.usac.edu.gt/errores/identificador-usuario-invalido",
              "title": "Identificador de usuario inválido",
              "status": 400,
              "detail": "El identificador del usuario autenticado no tiene formato UUID válido.",
              "instance": "/api/v1/notificaciones/019b9000-0900-7000-8000-000000000201/lectura"
            }
            """)))
  @ApiResponse(
      responseCode = "404",
      description = "Notificación no encontrada o pertenece a otro usuario",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = ProblemDetail.class),
              examples =
                  @ExampleObject(
                      summary = "Notificacion no encontrada",
                      value =
                          """
            {
              "type": "https://sgp.cunoc.usac.edu.gt/errores/notificacion-no-encontrada",
              "title": "Notificación no encontrada",
              "status": 404,
              "detail": "No existe la notificación especificada para el usuario actual.",
              "instance": "/api/v1/notificaciones/019b9000-0900-7000-8000-000000000201/lectura"
            }
            """)))
  @PatchMapping("/{id}/lectura")
  NotificacionDTO marcarComoLeida(@PathVariable UUID id, Principal usuario) {
    return servicio.marcarComoLeida(id, extraerUsuarioId(usuario));
  }

  private static UUID extraerUsuarioId(Principal usuario) {
    if (usuario == null || usuario.getName() == null) {
      throw new NegocioException(
          HttpStatus.UNAUTHORIZED,
          "sesion-invalida",
          "Sesión requerida",
          "No se encontró un usuario autenticado.");
    }
    try {
      return UUID.fromString(usuario.getName());
    } catch (IllegalArgumentException ex) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "identificador-usuario-invalido",
          "Identificador de usuario inválido",
          "El identificador del usuario autenticado no tiene formato UUID válido.");
    }
  }
}
