package com.cunoc.puentes.sync;

import java.util.Optional;
import java.util.UUID;

/**
 * Consulta de una identidad durable confirmada antes de efectos externos, sin conocer su dominio.
 * El núcleo consulta bajo su bloqueo usuario/clave. La implementación debe leer sin adquirir de
 * nuevo ese bloqueo ni bloquear recursos de negocio. La creación de intenciones debe realizarse
 * desde el callback del mismo núcleo, conservando el bloqueo aunque use una transacción nueva.
 */
public interface IntencionIdempotencia {

  Optional<HuellaIdempotencia> buscarHuella(UUID usuarioId, String clave);
}
