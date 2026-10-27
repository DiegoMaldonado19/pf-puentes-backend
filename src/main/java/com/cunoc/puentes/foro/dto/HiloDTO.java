package com.cunoc.puentes.foro.dto;

import com.cunoc.puentes.foro.CategoriaHilo;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Detalle de un hilo del foro")
public record HiloDTO(
    @Schema(description = "Identificador único (UUID v7)") UUID id,
    @JsonProperty("puente_id") @Schema(description = "Identificador del puente") UUID puenteId,
    @Schema(description = "Título del hilo") String titulo,
    @Schema(description = "Categoría del hilo") CategoriaHilo categoria,
    @JsonProperty("autor_id")
        @Schema(description = "Identificador del autor (nullable si creado por sistema)")
        UUID autorId,
    @JsonProperty("creado_en") @Schema(description = "Fecha de creación en UTC") Instant creadoEn,
    @Schema(description = "Indica si el hilo está cerrado a nuevos comentarios") boolean cerrado,
    @JsonProperty("autor_nombre")
        @Schema(description = "Nombre visible del autor; null para el sistema")
        String autorNombre) {

  public HiloDTO(
      UUID id,
      UUID puenteId,
      String titulo,
      CategoriaHilo categoria,
      UUID autorId,
      Instant creadoEn,
      boolean cerrado) {
    this(id, puenteId, titulo, categoria, autorId, creadoEn, cerrado, null);
  }

  public HiloDTO conAutorNombre(String nombre) {
    return new HiloDTO(id, puenteId, titulo, categoria, autorId, creadoEn, cerrado, nombre);
  }
}
