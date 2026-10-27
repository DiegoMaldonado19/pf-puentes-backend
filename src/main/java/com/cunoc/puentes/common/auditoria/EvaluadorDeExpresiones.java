package com.cunoc.puentes.common.auditoria;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/** Evalúa las expresiones de {@link Auditable} contra los parámetros y el retorno del método. */
@Component
class EvaluadorDeExpresiones {

  private final ExpressionParser analizador = new SpelExpressionParser();
  private final ParameterNameDiscoverer nombres = new DefaultParameterNameDiscoverer();

  /** Lanza {@code ParseException} si la expresión no es válida. */
  void validar(String expresion) {
    analizador.parseExpression(expresion);
  }

  /** Vacío si la expresión aún no se puede resolver (depende de {@code #result} y no hay). */
  UUID idPrevio(String expresion, Method metodo, Object[] argumentos) {
    try {
      return aUuid(evaluar(expresion, metodo, argumentos, null));
    } catch (EvaluationException e) {
      return null;
    }
  }

  UUID id(String expresion, Method metodo, Object[] argumentos, Object resultado) {
    return aUuid(evaluar(expresion, metodo, argumentos, resultado));
  }

  /** Nulo si no hay expresión o da un texto en blanco: la columna no admite motivos vacíos. */
  String motivo(String expresion, Method metodo, Object[] argumentos) {
    if (expresion.isBlank()) {
      return null;
    }
    Object motivo = evaluar(expresion, metodo, argumentos, null);
    return motivo == null || motivo.toString().isBlank() ? null : motivo.toString().strip();
  }

  /** Verdadero si no hay condición: por defecto todo se audita. */
  boolean cumple(String condicion, Method metodo, Object[] argumentos, Object resultado) {
    return condicion.isBlank()
        || Boolean.TRUE.equals(evaluar(condicion, metodo, argumentos, resultado));
  }

  Map<String, Object> valores(
      String expresion, Method metodo, Object[] argumentos, Object resultado) {
    Object valor = evaluar(expresion, metodo, argumentos, resultado);
    if (valor instanceof Map<?, ?> mapa) {
      return SaneadorDeAuditoria.sanear(mapa);
    }
    throw new IllegalStateException("La proyección auditable debe devolver un mapa.");
  }

  private Object evaluar(String expresion, Method metodo, Object[] argumentos, Object resultado) {
    StandardEvaluationContext contexto = new StandardEvaluationContext();
    String[] parametros = nombres.getParameterNames(metodo);
    for (int i = 0; parametros != null && i < parametros.length; i++) {
      contexto.setVariable(parametros[i], argumentos[i]);
    }
    contexto.setVariable("result", resultado);
    return analizador.parseExpression(expresion).getValue(contexto);
  }

  private static UUID aUuid(Object valor) {
    return switch (valor) {
      case null -> null;
      case UUID uuid -> uuid;
      case String texto -> UUID.fromString(texto);
      default ->
          throw new IllegalStateException(
              "El id auditable debe ser un UUID, no %s"
                  .formatted(valor.getClass().getSimpleName()));
    };
  }
}
