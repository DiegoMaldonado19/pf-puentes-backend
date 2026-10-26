package com.cunoc.puentes.inspeccion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.UUID;

public record DatosPermanentesDTO(
    Map<String, Object> datos,
    @JsonProperty("origen_inspeccion_id") UUID origenInspeccionId,
    @JsonProperty("anio_construccion") Integer anioConstruccion) {}
