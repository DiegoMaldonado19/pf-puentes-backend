package com.cunoc.puentes.archivo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public record SubirDocumentoDTO(
    @Schema(description = "UUID v7 generado en el cliente (DT-OFF-05)") @NotNull UUID id,
    @Schema(description = "PDF de hasta 20 MB (RN-ARC-04)") @NotNull MultipartFile pdf) {}
