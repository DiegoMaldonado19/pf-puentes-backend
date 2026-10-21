package com.cunoc.puentes.puente.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** El código no viene del cliente: lo asigna el sistema (RN-INV-02). */
@Schema(description = "Alta de un puente por un Administrador")
public record CrearPuenteDTO(
    @NotBlank @Size(max = 200) @Schema(example = "Puente Las Rosas") String nombre,
    @JsonProperty("departamento_codigo")
        @NotBlank
        @Pattern(regexp = "\\d{2}", message = "debe ser el código INE de dos dígitos")
        @Schema(example = "09")
        String departamentoCodigo,
    @JsonProperty("municipio_codigo")
        @NotBlank
        @Pattern(regexp = "\\d{4}", message = "debe ser el código INE de cuatro dígitos")
        @Schema(example = "0901")
        String municipioCodigo,
    @NotBlank @Size(max = 100) @Schema(example = "CA-1") String ruta,
    @NotNull
        @DecimalMin(value = "13.5", message = "está fuera de Guatemala")
        @DecimalMax(value = "18.0", message = "está fuera de Guatemala")
        @Schema(description = "WGS84", example = "14.8347")
        Double latitud,
    @NotNull
        @DecimalMin(value = "-92.5", message = "está fuera de Guatemala")
        @DecimalMax(value = "-88.0", message = "está fuera de Guatemala")
        @Schema(description = "WGS84", example = "-91.518")
        Double longitud,
    @JsonProperty("anio_construccion") @Min(1800) @Max(2100) @Schema(example = "1985")
        Integer anioConstruccion,
    @JsonProperty("confirmar_duplicado")
        @Schema(
            description =
                "true si el usuario ya vio la advertencia de puentes a menos de 100 m (RN-INV-06)")
        Boolean confirmarDuplicado) {

  /** Ausente equivale a no confirmado: primero se muestra la advertencia (RN-INV-06). */
  public boolean confirmado() {
    return Boolean.TRUE.equals(confirmarDuplicado);
  }
}
