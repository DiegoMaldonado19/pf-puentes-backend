package com.cunoc.puentes.foro.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

@Schema(description = "Datos para crear un comentario o respuesta en un hilo")
public record CrearComentarioDTO(
    @NotBlank(message = "El texto no puede estar vacío.")
        @Schema(
            description = "Texto del comentario",
            example = "Se observó en la última visita; adjunto referencia en el hilo.")
        String texto,
    @JsonProperty("padre_id")
        @Schema(
            description =
                "Identificador del comentario raíz al que responde (opcional, null para comentario raíz)",
            nullable = true)
        UUID padreId,
    @JsonProperty("elemento_ref")
        @Schema(
            description =
                "Referencia opcional al elemento de la inspección al que se ancla el comentario",
            nullable = true)
        String elementoRef) {

  public CrearComentarioDTO(String texto, UUID padreId) {
    this(texto, padreId, null);
  }

  public CrearComentarioDTO(String texto) {
    this(texto, null, null);
  }
}
