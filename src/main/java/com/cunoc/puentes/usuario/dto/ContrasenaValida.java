package com.cunoc.puentes.usuario.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** La contraseña cumple la política de RN-USR-06. Un valor nulo lo rechaza {@code @NotNull}. */
@Documented
@Constraint(validatedBy = ValidadorDeContrasena.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ContrasenaValida {

  String message() default
      "No cumple la política: mínimo 10 caracteres, con al menos una letra y un dígito";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
