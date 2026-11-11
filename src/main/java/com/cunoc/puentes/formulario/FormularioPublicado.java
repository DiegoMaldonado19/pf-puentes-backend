package com.cunoc.puentes.formulario;

import java.util.UUID;

/**
 * Se publica dentro de la transacción de publicación de una versión (B2). Los módulos vecinos la
 * escuchan con {@code @EventListener} síncrono, igual que {@code InspeccionPublicada}.
 *
 * @param anteriorId versión que estaba ACTIVA y acaba de retirarse; null si no había ninguna
 * @param nuevoId versión recién publicada
 */
public record FormularioPublicado(UUID anteriorId, UUID nuevoId) {}
