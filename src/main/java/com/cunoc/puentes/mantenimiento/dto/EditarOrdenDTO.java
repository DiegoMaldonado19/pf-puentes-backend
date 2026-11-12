package com.cunoc.puentes.mantenimiento.dto;

import com.cunoc.puentes.mantenimiento.TipoOrden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Corrección de una orden abierta: solo el tipo y la descripción. Una orden ejecutada, descartada o
 * cancelada ya no se edita.
 */
@Schema(description = "Corrección del tipo o la descripción de una orden abierta")
public record EditarOrdenDTO(
    TipoOrden tipo,
    @Size(max = 2000) @Schema(description = "No puede quedar vacía") String descripcion) {}
