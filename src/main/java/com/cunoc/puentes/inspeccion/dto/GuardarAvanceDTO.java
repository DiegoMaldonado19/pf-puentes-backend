package com.cunoc.puentes.inspeccion.dto;

import com.cunoc.puentes.inspeccion.EstadoInspeccion;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GuardarAvanceDTO(
    @NotNull Map<String, Object> datos,
    @NotNull Long version,
    @JsonProperty("fecha_inspeccion") LocalDate fechaInspeccion,
    @JsonProperty("grupos_sin_danos") List<String> gruposSinDanos,
    @JsonProperty("puente_id") UUID puenteId,
    @JsonProperty("autor_id") UUID autorId,
    @JsonProperty("formulario_version_id") UUID formularioVersionId,
    EstadoInspeccion estado,
    @JsonProperty("indice_condicion") BigDecimal indiceCondicion) {}
