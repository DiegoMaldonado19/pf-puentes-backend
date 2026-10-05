package com.cunoc.puentes.archivo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.BindParam;
import org.springframework.web.multipart.MultipartFile;

/** Lo que entrega {@code <app-captura-foto>} (contrato 9), como multipart/form-data. */
public record SubirFotoDTO(
    @Schema(description = "UUID v7 generado en el cliente (DT-OFF-05)") @NotNull UUID id,
    @Schema(description = "WebP o JPEG, lado mayor de 1600 px (RN-ARC-02)") @NotNull
        MultipartFile foto,
    @Schema(description = "La misma foto, a 300 px (DT-ALM-04)") @NotNull MultipartFile miniatura,
    @Schema(name = "elemento_ref", description = "Elemento al que se ancla (RN-ARC-01)")
        @BindParam("elemento_ref")
        @Size(max = 200)
        String elementoRef,
    @DecimalMin("-90") @DecimalMax("90") Double latitud,
    @DecimalMin("-180") @DecimalMax("180") Double longitud,
    @Schema(name = "capturada_en", description = "ISO 8601 en UTC (RN-ARC-03)")
        @BindParam("capturada_en")
        @NotNull
        Instant capturadaEn) {}
