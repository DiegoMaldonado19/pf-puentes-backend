package com.cunoc.puentes.inspeccion;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

interface AcompananteInspeccionRepository extends Repository<AcompananteInspeccion, UUID> {

  AcompananteInspeccion save(AcompananteInspeccion acompanante);

  List<AcompananteInspeccion> findByInspeccion_Id(UUID inspeccionId);

  void deleteByInspeccion_Id(UUID inspeccionId);
}
