package com.cunoc.puentes.puente;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Sin borrado físico en el servicio: la baja es lógica (RN-INV-08). */
interface PuenteRepository extends JpaRepository<Puente, UUID>, JpaSpecificationExecutor<Puente> {

  @EntityGraph(attributePaths = "municipio.departamento")
  Optional<Puente> findConCatalogoById(UUID id);

  // Una sola consulta con municipio y departamento, sin N+1 al armar cada fila
  @Override
  @EntityGraph(attributePaths = "municipio.departamento")
  Page<Puente> findAll(Specification<Puente> filtro, Pageable pagina);
}
