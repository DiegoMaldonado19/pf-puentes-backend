package com.cunoc.puentes.archivo;

import com.cunoc.puentes.inspeccion.EstadoInspeccion;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ArchivoRepository extends JpaRepository<Archivo, UUID> {

  long countByInspeccionIdAndTipoIn(UUID inspeccionId, Collection<TipoArchivo> tipos);

  List<Archivo> findByInspeccionIdOrderByCreadoEn(UUID inspeccionId);

  long countByInspeccionIdAndTipoInAndPurgadoEnIsNull(
      UUID inspeccionId, Collection<TipoArchivo> tipos);

  List<Archivo> findByOrdenMantenimientoIdOrderByCreadoEn(UUID ordenId);

  long countByOrdenMantenimientoId(UUID ordenId);

  // ADR 0010: otro módulo se lee por SQL, sin importar su entidad
  @Query(
      value =
          """
          SELECT puente_id AS "puenteId", autor_id AS "autorId", estado
          FROM inspeccion
          WHERE id = :id AND eliminado_en IS NULL
          """,
      nativeQuery = true)
  Optional<InspeccionDelArchivo> buscarInspeccion(UUID id);

  @Query(value = "SELECT puente_id FROM orden_mantenimiento WHERE id = :id", nativeQuery = true)
  Optional<UUID> buscarPuenteDeOrden(UUID id);

  // RN-ARC-06: la última modificación del borrador es su última actividad
  @Query(
      value =
          """
          SELECT a.* FROM archivo a JOIN inspeccion i ON i.id = a.inspeccion_id
          WHERE i.estado = 'BORRADOR' AND i.actualizado_en < :limite AND a.purgado_en IS NULL
          """,
      nativeQuery = true)
  List<Archivo> buscarParaPurgar(Instant limite);

  interface InspeccionDelArchivo {

    UUID getPuenteId();

    UUID getAutorId();

    EstadoInspeccion getEstado();
  }
}
