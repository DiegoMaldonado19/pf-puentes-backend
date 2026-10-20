package com.cunoc.puentes.mantenimiento.dto;

import com.cunoc.puentes.mantenimiento.EstadoOrden;
import com.cunoc.puentes.mantenimiento.Prioridad;
import com.cunoc.puentes.mantenimiento.TipoOrden;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Contrato de lectura de MTO-01; no es una entrada para crear órdenes ni cambiar su estado. */
public record OrdenMantenimientoDTO(
    UUID id,
    @JsonProperty("puente_id") UUID puenteId,
    @JsonProperty("inspeccion_id") UUID inspeccionId,
    TipoOrden tipo,
    EstadoOrden estado,
    String descripcion,
    @JsonProperty("elemento_ref") String elementoRef,
    @JsonProperty("prioridad_sugerida") Prioridad prioridadSugerida,
    Prioridad prioridad,
    @JsonProperty("justificacion_prioridad") String justificacionPrioridad,
    @JsonProperty("fecha_programada") LocalDate fechaProgramada,
    @JsonProperty("fecha_ejecucion") LocalDate fechaEjecucion,
    String responsable,
    String motivo,
    @JsonProperty("creado_en") Instant creadoEn,
    @JsonProperty("actualizado_en") Instant actualizadoEn) {}
