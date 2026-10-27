package com.cunoc.puentes.foro.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Comentario o respuesta de un hilo del foro")
public record ComentarioDTO(
    @Schema(description = "Identificador único (UUID v7)") UUID id,
    @JsonProperty("hilo_id") @Schema(description = "Identificador del hilo") UUID hiloId,
    @JsonProperty("autor_id") @Schema(description = "Identificador del autor") UUID autorId,
    @Schema(description = "Texto del comentario (null si está eliminado)") String texto,
    @JsonProperty("creado_en") @Schema(description = "Fecha de publicación en UTC")
        Instant creadoEn,
    @JsonProperty("padre_id")
        @Schema(
            description =
                "Identificador del comentario raíz al que responde (null si es comentario raíz)")
        UUID padreId,
    @Schema(description = "Indica si el comentario fue eliminado lógicamente por su autor")
        boolean eliminado,
    @JsonProperty("autor_nombre") @Schema(description = "Nombre visible del autor")
        String autorNombre,
    @JsonProperty("elemento_ref")
        @Schema(
            description =
                "Referencia opcional al elemento de la inspección al que se ancla el comentario",
            nullable = true)
        String elementoRef) {

  public ComentarioDTO(
      UUID id,
      UUID hiloId,
      UUID autorId,
      String texto,
      Instant creadoEn,
      UUID padreId,
      boolean eliminado,
      String autorNombre) {
    this(id, hiloId, autorId, texto, creadoEn, padreId, eliminado, autorNombre, null);
  }

  public ComentarioDTO(
      UUID id,
      UUID hiloId,
      UUID autorId,
      String texto,
      Instant creadoEn,
      UUID padreId,
      boolean eliminado) {
    this(id, hiloId, autorId, texto, creadoEn, padreId, eliminado, null, null);
  }

  public ComentarioDTO conAutorNombre(String nombre) {
    return new ComentarioDTO(
        id, hiloId, autorId, texto, creadoEn, padreId, eliminado, nombre, elementoRef);
  }

  public ComentarioDTO(UUID id, UUID hiloId, UUID autorId, String texto, Instant creadoEn) {
    this(id, hiloId, autorId, texto, creadoEn, null, false, null, null);
  }
}
