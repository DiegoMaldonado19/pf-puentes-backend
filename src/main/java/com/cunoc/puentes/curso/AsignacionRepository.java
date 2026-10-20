package com.cunoc.puentes.curso;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AsignacionRepository extends JpaRepository<Asignacion, UUID> {
  boolean existsByInscripcionIdAndPuenteId(UUID inscripcionId, UUID puenteId);

  @EntityGraph(attributePaths = {"inscripcion", "inscripcion.curso"})
  Page<Asignacion> findByInscripcionId(UUID inscripcionId, Pageable pagina);

  @EntityGraph(attributePaths = {"inscripcion", "inscripcion.curso"})
  @Query(
      """
      select a from Asignacion a where a.inscripcion.estudianteId = :estudianteId
      and a.inscripcion.curso.vigente = true
      and exists (select 1 from Puente p where p.id = a.puenteId and p.activo = true)
      """)
  Page<Asignacion> buscarVigentes(UUID estudianteId, Pageable pagina);

  @EntityGraph(attributePaths = {"inscripcion", "inscripcion.curso"})
  Optional<Asignacion> findByIdAndInscripcionEstudianteId(UUID id, UUID estudianteId);
}
