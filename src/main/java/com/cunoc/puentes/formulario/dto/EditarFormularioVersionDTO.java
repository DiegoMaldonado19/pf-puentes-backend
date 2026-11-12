package com.cunoc.puentes.formulario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(description = "Datos para editar el contenido de un borrador existente")
public record EditarFormularioVersionDTO(
    @Schema(
            description =
                "JSON Schema completo actualizado (uso avanzado). Se ignora cuando se envían campos_extra")
        Map<String, Object> esquema,
    @JsonProperty("campos_extra")
        @Schema(
            description =
                "Campos adicionales sobre el esquema base; tienen prioridad sobre esquema")
        List<CampoExtraDTO> camposExtra) {

  /** Forma previa a B2: solo esquema completo. */
  public EditarFormularioVersionDTO(Map<String, Object> esquema) {
    this(esquema, null);
  }
}
