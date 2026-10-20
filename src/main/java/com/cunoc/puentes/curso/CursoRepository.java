package com.cunoc.puentes.curso;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CursoRepository extends JpaRepository<Curso, UUID> {
  Page<Curso> findByCatedraticoId(UUID usuarioId, Pageable pagina);

  Optional<Curso> findByIdAndCatedraticoId(UUID id, UUID usuarioId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Curso c where c.id = :id and c.catedraticoId = :usuarioId")
  Optional<Curso> bloquearPropio(UUID id, UUID usuarioId);
}
