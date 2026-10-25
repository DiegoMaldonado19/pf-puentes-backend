package com.cunoc.puentes.inspeccion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Consultas de inspección. Sin borrado físico (DT-BD-11); la baja es lógica. */
interface InspeccionRepository extends Repository<Inspeccion, UUID> {

  Inspeccion save(Inspeccion inspeccion);

  Optional<Inspeccion> findById(UUID id);

  Optional<Inspeccion> findByIdAndEliminadoEnIsNull(UUID id);

  @EntityGraph(attributePaths = "supersedeA")
  List<Inspeccion> findByAutorIdAndEliminadoEnIsNullOrderByCreadoEnDesc(UUID autorId);

  @EntityGraph(attributePaths = "supersedeA")
  Page<Inspeccion> findByAutorIdAndEliminadoEnIsNullOrderByCreadoEnDesc(
      UUID autorId, Pageable pagina);

  List<Inspeccion> findByPuenteIdAndEliminadoEnIsNull(UUID puenteId);

  List<Inspeccion> findByPuenteIdAndEstadoAndEliminadoEnIsNull(
      UUID puenteId, EstadoInspeccion estado);

  List<Inspeccion> findByCursoIdAndEstado(UUID cursoId, EstadoInspeccion estado);

  @Query(
      """
      select i from Inspeccion i
      where i.estado = com.cunoc.puentes.inspeccion.EstadoInspeccion.BORRADOR
        and i.eliminadoEn is null
        and i.ultimaActividadEn <= :corte
        and i.avisoAbandonoEn is null
      """)
  List<Inspeccion> findBorradoresInactivosSinAviso(Instant corte);

  Optional<Inspeccion> findBySupersedeA_IdAndEliminadoEnIsNullAndEstadoNot(
      UUID originalId, EstadoInspeccion estado);

  Optional<Inspeccion>
      findFirstByPuenteIdAndEstadoAndEliminadoEnIsNullOrderByFechaInspeccionDescPublicadaEnDesc(
          UUID puenteId, EstadoInspeccion estado);

  @Query(value = "SELECT rol FROM usuario WHERE id = :id", nativeQuery = true)
  Optional<String> rolDeUsuario(UUID id);

  @Query(value = "SELECT estado FROM usuario WHERE id = :id", nativeQuery = true)
  Optional<String> estadoDeUsuario(UUID id);
}
