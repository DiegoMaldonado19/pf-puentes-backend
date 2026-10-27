package com.cunoc.puentes.revision;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/** Resumen autorizado, sin respuestas privadas ni calificaciones. */
public record BandejaRevisionDTO(
    @JsonProperty("inspeccion_id") UUID inspeccionId,
    @JsonProperty("curso_nombre") String cursoNombre,
    @JsonProperty("autor_nombre") String autorNombre,
    @JsonProperty("puente_codigo") String puenteCodigo,
    String estado,
    @JsonProperty("enviada_en") Instant enviadaEn,
    @JsonProperty("revision_id") UUID revisionId) {}
