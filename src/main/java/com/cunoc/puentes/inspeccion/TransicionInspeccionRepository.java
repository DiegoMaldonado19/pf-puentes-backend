package com.cunoc.puentes.inspeccion;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Historial de solo inserción. El UPDATE/DELETE lo deniega la base al rol de la aplicación. */
interface TransicionInspeccionRepository extends Repository<TransicionInspeccion, UUID> {

  TransicionInspeccion save(TransicionInspeccion transicion);

  List<TransicionInspeccion> findByInspeccion_IdOrderByCreadoEnAsc(UUID inspeccionId);
}
