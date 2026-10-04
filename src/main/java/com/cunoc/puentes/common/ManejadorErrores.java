package com.cunoc.puentes.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Errores de toda la API en formato RFC 7807 (DT-BE-05, DT-BE-06). */
@RestControllerAdvice
class ManejadorErrores extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ex.getBody()
        .setProperty(
            ErrorCampo.PROPIEDAD,
            ex.getFieldErrors().stream()
                .map(error -> new ErrorCampo(error.getField(), error.getDefaultMessage()))
                .toList());
    return super.handleMethodArgumentNotValid(ex, headers, status, request);
  }

  // Spring Security las convierte en 401 o 403; sin esto caerían en el 500 de abajo
  @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
  void seguridad(RuntimeException ex) {
    throw ex;
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail inesperado(Exception ex, WebRequest request) {
    log.error("500 {}", request.getDescription(false), ex);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado.");
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ResponseEntity<Object> respuesta =
        super.handleExceptionInternal(ex, body, headers, status, request);
    if (status.is5xxServerError()) {
      log.error("{} {}", status.value(), request.getDescription(false), ex);
    } else {
      // Sin ex.getMessage(): en los 400 de validación trae los valores rechazados (DT-SEC-12)
      log.warn(
          "{} {} {} en {}: {}",
          status.value(),
          request.getDescription(false),
          ex.getClass().getSimpleName(),
          origen(ex),
          respuesta != null && respuesta.getBody() instanceof ProblemDetail problema
              ? problema.getDetail()
              : null);
    }
    return respuesta;
  }

  private static Object origen(Exception ex) {
    StackTraceElement[] traza = ex.getStackTrace();
    return traza.length > 0 ? traza[0] : "?";
  }
}
