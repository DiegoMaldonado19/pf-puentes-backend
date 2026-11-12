package com.cunoc.puentes.revision;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface RevisionRepository extends JpaRepository<Revision, UUID> {
  Optional<Revision> findByInspeccionIdAndVeredictoIsNull(UUID inspeccionId);

  Page<Revision> findByInspeccionIdOrderByCreadoEnDescIdDesc(UUID inspeccionId, Pageable pagina);

  /** Revisiones ya emitidas de las inspecciones dadas, de la más reciente a la más antigua. */
  List<Revision> findByInspeccionIdInAndVeredictoIsNotNullOrderByCreadoEnDesc(
      Collection<UUID> inspeccionIds);
}
