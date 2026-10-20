package com.cunoc.puentes.indice;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface PesoElementoRepository extends JpaRepository<PesoElemento, UUID> {

  @EntityGraph(attributePaths = "versionPesos")
  Page<PesoElemento> findByVersionPesosId(UUID versionPesosId, Pageable pageable);
}
