package com.cunoc.puentes.inspeccion.puerto;

import java.time.Instant;

/**
 * Lo que el autor necesita de la última revisión emitida (B3): el veredicto por su nombre de enum,
 * cuándo se emitió y cuántas observaciones de ese ciclo siguen sin resolver.
 */
public record ResumenRevision(
    String ultimoVeredicto, Instant emitidoEn, int observacionesPendientes) {}
