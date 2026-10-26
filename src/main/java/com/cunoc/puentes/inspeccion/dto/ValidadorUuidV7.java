package com.cunoc.puentes.inspeccion.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.UUID;

class ValidadorUuidV7 implements ConstraintValidator<UuidV7, UUID> {

  @Override
  public boolean isValid(UUID id, ConstraintValidatorContext contexto) {
    return id == null || id.version() == 7;
  }
}
