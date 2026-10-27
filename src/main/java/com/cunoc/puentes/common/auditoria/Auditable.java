package com.cunoc.puentes.common.auditoria;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un método de servicio cuya ejecución se registra en la bitácora (RN-USR-09, DT-BD-12).
 *
 * <p>El aspecto lee la fila de {@link #entidad()} antes y después de ejecutar el método, así que el
 * método debe cambiar la entidad con JPA y estar en un bean de Spring invocado desde fuera (no
 * desde otro método del mismo bean). La fila de auditoría se escribe en la misma transacción: si el
 * método falla no queda registro y, si falla el registro, se revierte el método.
 *
 * <pre>{@code
 * @Auditable(accion = Acciones.DAR_DE_BAJA, entidad = "usuario", entidadId = "#id", motivo = "#motivo")
 * public void darDeBaja(UUID id, String motivo) { ... }
 *
 * @Auditable(accion = Acciones.CREAR, entidad = "puente", entidadId = "#result.id")
 * public PuenteDTO crear(CrearPuenteDTO datos) { ... }
 * }</pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

  /** Una de {@link Acciones#CATALOGO}; otro valor impide que la aplicación arranque. */
  String accion();

  /**
   * Nombre de la entidad en snake_case, igual al de su clase: {@code OrdenMantenimiento} es
   * "orden_mantenimiento".
   */
  String entidad();

  /**
   * Expresión SpEL con el id (UUID) de la fila afectada. Ve los parámetros por nombre ({@code #id})
   * y el retorno como {@code #result}. Si solo depende de los parámetros se guarda el valor
   * anterior; si depende de {@code #result} (una creación) no hay valor anterior.
   */
  String entidadId() default "#result";

  /**
   * Expresión SpEL con la justificación escrita, para acciones que la exigen (bajas, moderación).
   */
  String motivo() default "";

  /**
   * Expresión SpEL booleana evaluada después de ejecutar el método, con los parámetros y {@code
   * #result}. Si da falso no se escribe la fila (el método sí se confirma). Vacía: siempre se
   * audita. Sirve para operaciones que solo a veces son auditables, como el bloqueo de una cuenta
   * dentro del registro de un intento fallido.
   */
  String condicion() default "";

  /** Proyección SpEL opcional del estado anterior; evita copiar texto privado a la bitácora. */
  String valorAnterior() default "";

  /** Proyección SpEL opcional del estado posterior, incluidos cambios en cascada. */
  String valorNuevo() default "";
}
