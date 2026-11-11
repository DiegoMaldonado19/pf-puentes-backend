package com.cunoc.puentes.mantenimiento.dto;

import com.cunoc.puentes.mantenimiento.Prioridad;
import com.cunoc.puentes.mantenimiento.TipoOrden;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Alta manual: entra como PROPUESTA, igual que las que genera un daño publicado (RN-MTO-02). */
@Schema(description = "Alta manual de una orden de mantenimiento")
public record CrearOrdenDTO(
    @NotNull TipoOrden tipo,
    @NotBlank @Size(max = 2000) String descripcion,
    @JsonProperty("elemento_ref") @Size(max = 200) String elementoRef,
    @NotNull Prioridad prioridad,
    @JsonProperty("inspeccion_id") UUID inspeccionId) {}
