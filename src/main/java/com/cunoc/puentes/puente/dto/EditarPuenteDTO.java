package com.cunoc.puentes.puente.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Sin código ni municipio (el código los contiene y es inmutable, RN-INV-02) ni campos derivados de
 * la inspección (RN-INV-09).
 */
@Schema(description = "Edición de los datos descriptivos de un puente")
public record EditarPuenteDTO(
    @NotBlank @Size(max = 200) String nombre,
    @NotBlank @Size(max = 100) String ruta,
    @NotNull
        @DecimalMin(value = "13.5", message = "está fuera de Guatemala")
        @DecimalMax(value = "18.0", message = "está fuera de Guatemala")
        Double latitud,
    @NotNull
        @DecimalMin(value = "-92.5", message = "está fuera de Guatemala")
        @DecimalMax(value = "-88.0", message = "está fuera de Guatemala")
        Double longitud,
    @JsonProperty("anio_construccion") @Min(1800) @Max(2100) Integer anioConstruccion,
    @JsonProperty("confirmar_duplicado") Boolean confirmarDuplicado) {

  /** Ausente equivale a no confirmado: primero se muestra la advertencia (RN-INV-06). */
  public boolean confirmado() {
    return Boolean.TRUE.equals(confirmarDuplicado);
  }
}
