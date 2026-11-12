package com.cunoc.puentes.indice;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface PesoElementoRepository extends JpaRepository<PesoElemento, UUID> {

  @EntityGraph(attributePaths = "versionPesos")
  Page<PesoElemento> findByVersionPesosId(UUID versionPesosId, Pageable pageable);

  /** B2: copia de un juego al publicar una versión; orden estable por referencia. */
  List<PesoElemento> findByVersionPesosIdOrderByElementoRefAsc(UUID versionPesosId);
}
