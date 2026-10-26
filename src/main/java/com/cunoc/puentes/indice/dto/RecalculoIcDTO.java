package com.cunoc.puentes.indice.dto;

import com.cunoc.puentes.indice.Anulacion;
import com.cunoc.puentes.indice.EstadoCondicion;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Registro histórico fechado de un recálculo bajo otro juego de pesos (RN-IC-08). */
public record RecalculoIcDTO(
    UUID id,
    @JsonProperty("inspeccion_id") UUID inspeccionId,
    @JsonProperty("version_pesos_id") UUID versionPesosId,
    @JsonProperty("formulario_version_id") UUID formularioVersionId,
    String acta,
    @JsonProperty("vigente_desde") Instant vigenteDesde,
    @JsonProperty("indice_condicion") BigDecimal indiceCondicion,
    @JsonProperty("estado_calculado") EstadoCondicion estadoCalculado,
    Anulacion anulacion,
    @JsonProperty("creado_en") Instant creadoEn) {}
