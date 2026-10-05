package com.cunoc.puentes.archivo.dto;

import com.cunoc.puentes.archivo.TipoArchivo;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Foto o PDF de una inspección. Las URL vencen a los 15 minutos (DT-SEC-09). */
public record ArchivoDTO(
    UUID id,
    TipoArchivo tipo,
    @JsonProperty("elemento_ref") String elementoRef,
    Double latitud,
    Double longitud,
    @JsonProperty("capturada_en") Instant capturadaEn,
    @Schema(description = "Bytes de la foto o del PDF") long tamano,
    @Schema(description = "Vacía si el archivo se purgó (RN-ARC-06)") String url,
    @JsonProperty("url_miniatura") @Schema(description = "Vacía en los PDF") String urlMiniatura) {}
