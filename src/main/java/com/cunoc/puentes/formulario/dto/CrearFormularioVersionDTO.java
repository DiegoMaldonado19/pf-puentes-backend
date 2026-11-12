package com.cunoc.puentes.formulario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

@Schema(description = "Datos para registrar un nuevo borrador de formulario")
public record CrearFormularioVersionDTO(
    @NotBlank
        @Size(max = 20)
        @Schema(example = "V2.0.0", description = "Código único de la versión")
        String codigo,
    @Schema(
            description =
                "JSON Schema completo (uso avanzado). Se ignora cuando se envían campos_extra")
        Map<String, Object> esquema,
    @JsonProperty("campos_extra")
        @Schema(
            description =
                "Campos adicionales sobre el esquema base; tienen prioridad sobre esquema")
        List<CampoExtraDTO> camposExtra) {

  /** Forma previa a B2: solo código y esquema completo. */
  public CrearFormularioVersionDTO(String codigo, Map<String, Object> esquema) {
    this(codigo, esquema, null);
  }
}
