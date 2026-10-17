package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.NegocioException;
import org.springframework.http.HttpStatus;

/**
 * La cookie de renovación no sirve. Siempre el mismo mensaje: vencida, revocada, inexistente, de
 * una cuenta dada de baja o ausente son indistinguibles para quien llama.
 */
class SesionInvalidaException extends NegocioException {

  SesionInvalidaException() {
    super(
        HttpStatus.UNAUTHORIZED,
        "sesion-invalida",
        "La sesión no es válida",
        "La sesión venció o no existe. Inicia sesión de nuevo.");
  }
}
