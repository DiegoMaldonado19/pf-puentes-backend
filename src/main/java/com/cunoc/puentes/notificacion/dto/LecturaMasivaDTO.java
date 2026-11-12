package com.cunoc.puentes.notificacion.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cuántas notificaciones no leídas quedaron marcadas como leídas. */
@Schema(description = "Resultado de marcar todas las notificaciones como leídas")
public record LecturaMasivaDTO(
    @Schema(description = "Notificaciones que estaban sin leer y ahora están leídas")
        long marcadas) {}
