package com.cunoc.puentes.puente.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Los mismos campos obligatorios del alta (RN-INV-04); el resto lo completa el Administrador. */
@Schema(description = "Solicitud de alta de un puente por un Catedrático (RN-INV-07)")
public record CrearSolicitudPuenteDTO(
    @NotBlank @Size(max = 200) String nombre,
    @JsonProperty("departamento_codigo")
        @NotBlank
        @Pattern(regexp = "\\d{2}", message = "debe ser el código INE de dos dígitos")
        String departamentoCodigo,
    @JsonProperty("municipio_codigo")
        @NotBlank
        @Pattern(regexp = "\\d{4}", message = "debe ser el código INE de cuatro dígitos")
        String municipioCodigo,
    @NotBlank @Size(max = 100) String ruta,
    @NotNull
        @DecimalMin(value = "13.5", message = "está fuera de Guatemala")
        @DecimalMax(value = "18.0", message = "está fuera de Guatemala")
        Double latitud,
    @NotNull
        @DecimalMin(value = "-92.5", message = "está fuera de Guatemala")
        @DecimalMax(value = "-88.0", message = "está fuera de Guatemala")
        Double longitud,
    @JsonProperty("confirmar_duplicado") Boolean confirmarDuplicado) {

  /** Ausente equivale a no confirmado: primero se muestra la advertencia (RN-INV-06). */
  public boolean confirmado() {
    return Boolean.TRUE.equals(confirmarDuplicado);
  }
}
