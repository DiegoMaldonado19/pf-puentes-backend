package com.cunoc.puentes.puente.dto;

import com.cunoc.puentes.indice.EstadoCondicion;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Ficha de un puente del inventario")
public record PuenteDTO(
    UUID id,
    @Schema(example = "GT-09-0901-0001") String codigo,
    String nombre,
    String ruta,
    DepartamentoDTO departamento,
    MunicipioDTO municipio,
    @Schema(description = "WGS84 (EPSG:4326)") double latitud,
    @Schema(description = "WGS84 (EPSG:4326)") double longitud,
    UtmDTO utm,
    @JsonProperty("anio_construccion") Integer anioConstruccion,
    boolean activo,
    @JsonProperty("motivo_baja") String motivoBaja,
    @JsonProperty("estado_actual")
        @Schema(description = "null = Sin evaluar: no hay inspección publicada (RN-INV-10)")
        EstadoCondicion estadoActual,
    @JsonProperty("indice_condicion_actual") BigDecimal indiceCondicionActual,
    @JsonProperty("fecha_ultima_inspeccion") LocalDate fechaUltimaInspeccion,
    @JsonProperty("creado_en") Instant creadoEn,
    @JsonProperty("actualizado_en") Instant actualizadoEn) {}
