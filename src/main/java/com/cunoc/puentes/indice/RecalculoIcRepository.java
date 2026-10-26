package com.cunoc.puentes.indice;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

/** Interno al módulo; no expone borrado físico ni listados sin paginación (RN-IC-08, DT-BE-08). */
interface RecalculoIcRepository extends Repository<RecalculoIc, UUID> {

  RecalculoIc save(RecalculoIc recalculo);

  @EntityGraph(attributePaths = "versionPesos")
  Optional<RecalculoIc> findByInspeccionIdAndVersionPesosId(UUID inspeccionId, UUID versionPesosId);

  @EntityGraph(attributePaths = "versionPesos")
  Page<RecalculoIc> findByInspeccionId(UUID inspeccionId, Pageable pageable);
}

