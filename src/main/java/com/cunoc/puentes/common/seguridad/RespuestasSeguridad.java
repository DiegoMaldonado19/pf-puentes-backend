package com.cunoc.puentes.common.seguridad;

import com.cunoc.puentes.common.NegocioException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * 401 y 403 de la cadena de filtros, con el mismo cuerpo RFC 7807 que {@code ManejadorErrores}
 * (DT-BE-05). Los detalles son genéricos a propósito: no revelan por qué falló el token.
 */
final class RespuestasSeguridad {

  private static final Logger log = LoggerFactory.getLogger(RespuestasSeguridad.class);

  private final JsonMapper json;

  RespuestasSeguridad(JsonMapper json) {
    this.json = json;
  }

  AuthenticationEntryPoint noAutenticado() {
    return (request, response, ex) -> {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
      log.warn("401 {} {}", request.getMethod(), request.getRequestURI());
      escribir(
          request,
          response,
          new NegocioException(
              HttpStatus.UNAUTHORIZED,
              "no-autenticado",
              "Autenticación requerida",
              "Falta una sesión válida para esta operación."));
    };
  }

  void demasiadasSolicitudes(
      HttpServletRequest request, HttpServletResponse response, long reintentoSegundos)
      throws IOException {
    response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(reintentoSegundos));
    log.warn(
        "429 {} {} ip {}", request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
    escribir(
        request,
        response,
        new NegocioException(
            HttpStatus.TOO_MANY_REQUESTS,
            "demasiadas-solicitudes",
            "Demasiadas solicitudes",
            "Espera un momento antes de volver a intentarlo."));
  }

  AccessDeniedHandler accesoDenegado() {
    return (request, response, ex) -> {
      Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
      log.warn(
          "403 {} {} usuario {}",
          request.getMethod(),
          request.getRequestURI(),
          autenticacion == null ? "?" : autenticacion.getName());
      escribir(
          request,
          response,
          new NegocioException(
              HttpStatus.FORBIDDEN,
              "acceso-denegado",
              "Acceso denegado",
              "No tiene permiso para esta operación."));
    };
  }

  private void escribir(
      HttpServletRequest request, HttpServletResponse response, NegocioException error)
      throws IOException {
    ProblemDetail problema = error.getBody();
    problema.setInstance(URI.create(request.getRequestURI()));
    response.setStatus(problema.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(response.getOutputStream(), problema);
  }
}
