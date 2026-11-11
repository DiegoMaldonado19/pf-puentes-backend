package com.cunoc.puentes.revision;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ObservacionRevisionRepository extends JpaRepository<ObservacionAnclada, UUID> {
  List<ObservacionAnclada> findByRevision_IdOrderByCreadoEnAscIdAsc(UUID revisionId);

  // Solo el último ciclo: las observaciones de ciclos anteriores ya no bloquean el envío
  @Query(
      "SELECT COUNT(o) FROM ObservacionAnclada o"
          + " WHERE o.revision.inspeccionId = :inspeccionId AND o.resueltaEn IS NULL"
          + " AND o.revision.creadoEn = (SELECT MAX(r.creadoEn) FROM Revision r"
          + " WHERE r.inspeccionId = :inspeccionId)")
  long contarPendientes(@Param("inspeccionId") UUID inspeccionId);
}
