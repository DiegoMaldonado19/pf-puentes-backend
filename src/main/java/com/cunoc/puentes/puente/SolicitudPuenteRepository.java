package com.cunoc.puentes.puente;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SolicitudPuenteRepository
    extends JpaRepository<SolicitudPuente, UUID>, JpaSpecificationExecutor<SolicitudPuente> {

  @EntityGraph(attributePaths = "municipio.departamento")
  Optional<SolicitudPuente> findConCatalogoById(UUID id);

  @Override
  @EntityGraph(attributePaths = "municipio.departamento")
  Page<SolicitudPuente> findAll(Specification<SolicitudPuente> filtro, Pageable pagina);

  // Dos administradores no pueden resolver la misma solicitud a la vez
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM SolicitudPuente s WHERE s.id = :id")
  Optional<SolicitudPuente> bloquear(@Param("id") UUID id);
}
