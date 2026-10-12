package com.cunoc.puentes.curso;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface InscripcionRepository extends JpaRepository<Inscripcion, UUID> {
  @EntityGraph(attributePaths = "curso")
  Page<Inscripcion> findByCursoId(UUID cursoId, Pageable pagina);

  Optional<Inscripcion> findByIdAndCursoId(UUID id, UUID cursoId);

  boolean existsByCursoIdAndEstudianteId(UUID cursoId, UUID estudianteId);
}
