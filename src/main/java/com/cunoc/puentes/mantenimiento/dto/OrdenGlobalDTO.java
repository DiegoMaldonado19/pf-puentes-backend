package com.cunoc.puentes.mantenimiento.dto;

import com.cunoc.puentes.mantenimiento.EstadoOrden;
import com.cunoc.puentes.mantenimiento.Prioridad;
import com.cunoc.puentes.mantenimiento.TipoOrden;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Orden de mantenimiento del listado global, con el puente de origen (B9)")
public record OrdenGlobalDTO(
    UUID id,
    @JsonProperty("puente_id") UUID puenteId,
    @JsonProperty("puente_codigo") String puenteCodigo,
    @JsonProperty("puente_nombre") String puenteNombre,
    TipoOrden tipo,
    EstadoOrden estado,
    Prioridad prioridad,
    String descripcion,
    @JsonProperty("fecha_programada") LocalDate fechaProgramada,
    @JsonProperty("creado_en") Instant creadoEn) {}
