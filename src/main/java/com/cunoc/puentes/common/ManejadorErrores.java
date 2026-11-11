package com.cunoc.puentes.common;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/** Errores de toda la API en formato RFC 7807 (DT-BE-05, DT-BE-06). */
@RestControllerAdvice
class ManejadorErrores extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

  private static final String TIPO_DATOS_INVALIDOS = "datos-invalidos";
  private static final String TITULO_DATOS_INVALIDOS = "Datos no válidos";
  private static final String DETALLE_VALIDACION = "Hay campos con valores no válidos.";
  private static final String DETALLE_TIPO = "Hay valores con un tipo de dato no válido.";
  private static final String DETALLE_JSON =
      "El cuerpo de la solicitud no es JSON válido o está vacío.";
  private static final String MENSAJE_TIPO = "Tipo de dato no válido.";
  private static final String MENSAJE_ENUM = "Valor no permitido. Usa uno de: %s.";
  private static final String MENSAJE_OBLIGATORIO = "Es obligatorio.";
  private static final Pattern PATRON_RANGO =
      Pattern.compile("size must be between (\\d+) and (\\d+)");
  private static final Pattern PATRON_MAXIMO =
      Pattern.compile("must be less than or equal to (-?\\d+(\\.\\d+)?)");
  private static final Pattern PATRON_MINIMO =
      Pattern.compile("must be greater than or equal to (-?\\d+(\\.\\d+)?)");

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<ErrorCampo> errores =
        ex.getFieldErrors().stream()
            .map(
                error ->
                    new ErrorCampo(
                        snakeCase(error.getField()),
                        mensajeDeValidacion(error.getDefaultMessage())))
            .toList();
    return handleExceptionInternal(
        ex,
        problema(
            HttpStatus.BAD_REQUEST,
            TIPO_DATOS_INVALIDOS,
            TITULO_DATOS_INVALIDOS,
            DETALLE_VALIDACION,
            errores),
        headers,
        status,
        request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<ErrorCampo> errores = errorDeJson(ex.getCause());
    return handleExceptionInternal(
        ex,
        problema(
            HttpStatus.BAD_REQUEST,
            TIPO_DATOS_INVALIDOS,
            TITULO_DATOS_INVALIDOS,
            errores.isEmpty() ? DETALLE_JSON : DETALLE_TIPO,
            errores),
        headers,
        status,
        request);
  }

  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String nombre =
        ex instanceof MethodArgumentTypeMismatchException argumento
            ? argumento.getName()
            : ex.getPropertyName();
    List<ErrorCampo> errores =
        nombre == null
            ? List.of()
            : List.of(new ErrorCampo(snakeCase(nombre), mensajeDeTipo(ex.getRequiredType())));
    return handleExceptionInternal(
        ex,
        problema(
            HttpStatus.BAD_REQUEST,
            TIPO_DATOS_INVALIDOS,
            TITULO_DATOS_INVALIDOS,
            DETALLE_TIPO,
            errores),
        headers,
        status,
        request);
  }

  // Las convierte en 401 o 403 con el mismo cuerpo RFC 7807 el entry point y el handler de
  // common/seguridad/RespuestasSeguridad; sin esto caerían en el 500 de abajo
  @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
  void seguridad(RuntimeException ex) {
    throw ex;
  }

  // Violación de integridad (duplicado, clave foránea, nulo): es un conflicto con los datos
  // existentes, no un fallo del servidor. El detalle SQL no sale al cliente (DT-SEC-12).
  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail integridad(DataIntegrityViolationException ex, WebRequest request) {
    log.warn("409 {} {}", request.getDescription(false), origen(ex));
    return problema(
        HttpStatus.CONFLICT,
        "datos-en-conflicto",
        "Conflicto con los datos",
        "Los datos chocan con registros existentes o con referencias en uso.",
        List.of());
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

  private static List<ErrorCampo> errorDeJson(Throwable causa) {
    if (!(causa instanceof MismatchedInputException error)) {
      return List.of();
    }
    String ruta = rutaJson(error.getPath());
    if (ruta.isEmpty()) {
      return List.of();
    }
    return List.of(new ErrorCampo(ruta, mensajeDeTipo(error.getTargetType())));
  }

  /**
   * Traduce los mensajes por defecto de Jakarta Validation (en inglés). Los mensajes propios de
   * cada DTO ya están en español y pasan sin cambios.
   */
  static String mensajeDeValidacion(String mensaje) {
    if (mensaje == null) {
      return MENSAJE_OBLIGATORIO;
    }
    var rango = PATRON_RANGO.matcher(mensaje);
    if (rango.matches()) {
      return "Debe tener entre %s y %s caracteres.".formatted(rango.group(1), rango.group(2));
    }
    var maximo = PATRON_MAXIMO.matcher(mensaje);
    if (maximo.matches()) {
      return "Debe ser menor o igual a %s.".formatted(maximo.group(1));
    }
    var minimo = PATRON_MINIMO.matcher(mensaje);
    if (minimo.matches()) {
      return "Debe ser mayor o igual a %s.".formatted(minimo.group(1));
    }
    return switch (mensaje) {
      case "must not be null", "must not be blank", "must not be empty" -> MENSAJE_OBLIGATORIO;
      case "must be a well-formed email address" -> "El correo no tiene un formato válido.";
      default -> mensaje;
    };
  }

  private static String mensajeDeTipo(Class<?> tipo) {
    if (tipo != null && tipo.isEnum()) {
      String opciones =
          Arrays.stream(tipo.getEnumConstants())
              .map(constante -> ((Enum<?>) constante).name())
              .collect(Collectors.joining(", "));
      return MENSAJE_ENUM.formatted(opciones);
    }
    return MENSAJE_TIPO;
  }

  /** Ruta JSON con nombres de propiedad en snake_case, p. ej. {@code tramos[0].numero_tramos}. */
  private static String rutaJson(List<JacksonException.Reference> camino) {
    StringBuilder ruta = new StringBuilder();
    for (JacksonException.Reference paso : camino) {
      if (paso.getPropertyName() != null) {
        if (!ruta.isEmpty()) {
          ruta.append('.');
        }
        ruta.append(snakeSegmento(paso.getPropertyName()));
      } else if (paso.getIndex() >= 0) {
        ruta.append('[').append(paso.getIndex()).append(']');
      }
    }
    return ruta.toString();
  }

  /**
   * camelCase a snake_case por segmento, para que {@code campo} coincida con el nombre que envió el
   * cliente: {@code subestructura.estriboEntrada} → {@code subestructura.estribo_entrada}.
   */
  static String snakeCase(String ruta) {
    if (ruta == null) {
      return null;
    }
    return Arrays.stream(ruta.split("\\.", -1))
        .map(ManejadorErrores::snakeSegmento)
        .collect(Collectors.joining("."));
  }

  private static String snakeSegmento(String segmento) {
    return segmento
        .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
        .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
        .toLowerCase(Locale.ROOT);
  }

  private static ProblemDetail problema(
      HttpStatus estado, String tipo, String titulo, String detalle, List<ErrorCampo> errores) {
    ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
    problema.setType(URI.create(NegocioException.TIPO_BASE + tipo));
    problema.setTitle(titulo);
    if (!errores.isEmpty()) {
      problema.setProperty(ErrorCampo.PROPIEDAD, errores);
    }
    return problema;
  }

  private static Object origen(Exception ex) {
    StackTraceElement[] traza = ex.getStackTrace();
    return traza.length > 0 ? traza[0] : "?";
  }
}
