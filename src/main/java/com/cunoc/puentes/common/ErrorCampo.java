package com.cunoc.puentes.common;

/** Elemento de la propiedad {@code errores} de un ProblemDetail (DT-BE-05). */
public record ErrorCampo(String campo, String mensaje) {

  static final String PROPIEDAD = "errores";
}
