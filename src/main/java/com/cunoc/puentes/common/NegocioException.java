package com.cunoc.puentes.common;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Error esperado que llega al cliente como RFC 7807 (DT-BE-05). {@code tipo} va en kebab-case. */
public class NegocioException extends ErrorResponseException {

  private static final String TIPO_BASE = "https://sgp.cunoc.usac.edu.gt/errores/";

  public NegocioException(HttpStatus estado, String tipo, String titulo, String detalle) {
    super(estado, problema(estado, tipo, titulo, detalle), null);
  }

  public NegocioException conErrores(List<ErrorCampo> errores) {
    getBody().setProperty(ErrorCampo.PROPIEDAD, errores);
    return this;
  }

  private static ProblemDetail problema(
      HttpStatus estado, String tipo, String titulo, String detalle) {
    ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
    problema.setType(URI.create(TIPO_BASE + tipo));
    problema.setTitle(titulo);
    return problema;
  }
}
