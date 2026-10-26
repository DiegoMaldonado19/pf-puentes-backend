package com.cunoc.puentes.sync;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface CopiaConflictoRepository extends Repository<CopiaConflicto, UUID> {

  CopiaConflicto save(CopiaConflicto copia);

  List<CopiaConflicto> findByInspeccionIdOrderByCreadoEnDesc(UUID inspeccionId);
}
