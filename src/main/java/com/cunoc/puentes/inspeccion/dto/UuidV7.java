package com.cunoc.puentes.inspeccion.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** DT-OFF-05: el id lo genera el cliente como UUID versión 7. */
@Documented
@Constraint(validatedBy = ValidadorUuidV7.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface UuidV7 {

  String message() default "El id debe ser un UUID versión 7.";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
