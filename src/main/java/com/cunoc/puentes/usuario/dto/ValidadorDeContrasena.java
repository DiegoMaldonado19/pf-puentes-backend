package com.cunoc.puentes.usuario.dto;

import com.cunoc.puentes.usuario.PoliticaDeContrasena;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class ValidadorDeContrasena implements ConstraintValidator<ContrasenaValida, String> {

  @Override
  public boolean isValid(String contrasena, ConstraintValidatorContext contexto) {
    return contrasena == null || PoliticaDeContrasena.cumple(contrasena);
  }
}
