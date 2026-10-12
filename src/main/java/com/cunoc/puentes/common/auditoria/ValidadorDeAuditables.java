package com.cunoc.puentes.common.auditoria;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.ParseException;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Revisa al arrancar cada {@link Auditable} de la aplicación: el formato y el catálogo de la
 * acción, el formato y la existencia de la entidad y las expresiones. Así un error de un módulo
 * falla en el arranque y no en la primera llamada, ni (peor) en el CHECK de la base.
 */
@Component
class ValidadorDeAuditables implements SmartInitializingSingleton {

  private static final String PAQUETE_BASE = "com.cunoc.puentes";
  private static final Pattern ACCION = Pattern.compile("^[A-Z][A-Z0-9_]*$");
  private static final Pattern ENTIDAD = Pattern.compile("^[a-z][a-z0-9_]*$");

  private final ListableBeanFactory beans;
  private final ObjectProvider<EstadoDeEntidad> estados;
  private final EvaluadorDeExpresiones expresiones;

  ValidadorDeAuditables(
      ListableBeanFactory beans,
      ObjectProvider<EstadoDeEntidad> estados,
      EvaluadorDeExpresiones expresiones) {
    this.beans = beans;
    this.estados = estados;
    this.expresiones = expresiones;
  }

  @Override
  public void afterSingletonsInstantiated() {
    List<String> errores = new ArrayList<>();
    for (String nombre : beans.getBeanNamesForType(Object.class, true, false)) {
      Class<?> tipo = beans.getType(nombre, false);
      if (tipo != null && ClassUtils.getUserClass(tipo).getName().startsWith(PAQUETE_BASE)) {
        ReflectionUtils.doWithMethods(
            ClassUtils.getUserClass(tipo),
            metodo -> errores.addAll(validar(metodo)),
            metodo -> AnnotatedElementUtils.hasAnnotation(metodo, Auditable.class));
      }
    }
    if (!errores.isEmpty()) {
      throw new IllegalStateException(
          "Declaraciones @Auditable inválidas:\n- " + String.join("\n- ", errores));
    }
  }

  private List<String> validar(Method metodo) {
    Auditable auditable = AnnotatedElementUtils.findMergedAnnotation(metodo, Auditable.class);
    String donde = metodo.getDeclaringClass().getSimpleName() + "." + metodo.getName();
    List<String> errores = new ArrayList<>();
    if (!ACCION.matcher(auditable.accion()).matches()) {
      errores.add(
          "%s: la acción '%s' debe ir en MAYÚSCULAS_CON_GUION_BAJO"
              .formatted(donde, auditable.accion()));
    } else if (!Acciones.CATALOGO.contains(auditable.accion())) {
      errores.add(
          "%s: la acción '%s' no está en el catálogo de Acciones"
              .formatted(donde, auditable.accion()));
    }
    if (!ENTIDAD.matcher(auditable.entidad()).matches()) {
      errores.add(
          "%s: la entidad '%s' debe ir en snake_case minúscula"
              .formatted(donde, auditable.entidad()));
    } else if (estados.stream().anyMatch(estado -> !estado.existe(auditable.entidad()))) {
      errores.add(
          "%s: no existe una entidad JPA llamada '%s'".formatted(donde, auditable.entidad()));
    }
    for (String expresion : List.of(auditable.entidadId(), auditable.motivo())) {
      if (expresion.isBlank()) {
        continue; // el motivo es opcional
      }
      try {
        expresiones.validar(expresion);
      } catch (ParseException e) {
        errores.add("%s: la expresión '%s' no es válida".formatted(donde, expresion));
      }
    }
    return errores;
  }
}
