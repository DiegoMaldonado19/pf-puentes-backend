package com.cunoc.puentes.revision;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ObservacionRevisionRepository extends JpaRepository<ObservacionAnclada, UUID> {
  List<ObservacionAnclada> findByRevision_IdOrderByCreadoEnAscIdAsc(UUID revisionId);
}
