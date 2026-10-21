package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.indice.EstadoCondicion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contrato 4: lo publica P5 al publicar una inspección, ya con el IC calculado (contrato 3). P3, P8
 * y P9 lo escuchan con {@code @EventListener} síncrono en la misma transacción.
 *
 * @param estado el estado final de la inspección publicada (el confirmado si el revisor lo ajustó)
 * @param fecha fecha de la inspección (no la de publicación)
 */
public record InspeccionPublicada(
    UUID inspeccionId,
    UUID puenteId,
    BigDecimal indiceCondicion,
    EstadoCondicion estado,
    LocalDate fecha) {}
