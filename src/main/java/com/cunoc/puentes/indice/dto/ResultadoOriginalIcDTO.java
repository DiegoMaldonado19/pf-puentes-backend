package com.cunoc.puentes.indice.dto;

import com.cunoc.puentes.indice.Anulacion;
import com.cunoc.puentes.indice.EstadoCondicion;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Resultado oficial inmutable de la inspección publicada original para comparación (RN-IC-08). */
public record ResultadoOriginalIcDTO(
    @JsonProperty("inspeccion_id") UUID inspeccionId,
    @JsonProperty("puente_id") UUID puenteId,
    @JsonProperty("formulario_version_id") UUID formularioVersionId,
    @JsonProperty("version_pesos_id") UUID versionPesosId,
    String acta,
    @JsonProperty("vigente_desde") Instant vigenteDesde,
    @JsonProperty("indice_condicion") BigDecimal indiceCondicion,
    @JsonProperty("estado_calculado") EstadoCondicion estadoCalculado,
    Anulacion anulacion,
    @JsonProperty("estado_confirmado") EstadoCondicion estadoConfirmado,
    @JsonProperty("justificacion_estado") String justificacionEstado,
    @JsonProperty("publicada_en") Instant publicadaEn) {}
