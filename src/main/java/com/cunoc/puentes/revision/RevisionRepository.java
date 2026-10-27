package com.cunoc.puentes.revision;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RevisionRepository extends JpaRepository<Revision, UUID> {
  Optional<Revision> findByInspeccionIdAndVeredictoIsNull(UUID inspeccionId);
}
