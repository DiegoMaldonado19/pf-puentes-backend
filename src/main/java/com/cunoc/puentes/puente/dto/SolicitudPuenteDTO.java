package com.cunoc.puentes.puente.dto;

import com.cunoc.puentes.puente.EstadoSolicitud;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Solicitud de alta de un puente")
public record SolicitudPuenteDTO(
    UUID id,
    @JsonProperty("catedratico_id") UUID catedraticoId,
    String nombre,
    DepartamentoDTO departamento,
    MunicipioDTO municipio,
    String ruta,
    double latitud,
    double longitud,
    UtmDTO utm,
    EstadoSolicitud estado,
    @JsonProperty("motivo_rechazo") String motivoRechazo,
    @JsonProperty("administrador_id") UUID administradorId,
    @JsonProperty("puente_id") @Schema(description = "Puente creado al aprobarla") UUID puenteId,
    @JsonProperty("creado_en") Instant creadoEn,
    @JsonProperty("actualizado_en") Instant actualizadoEn) {}
