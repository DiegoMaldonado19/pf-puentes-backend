package com.cunoc.puentes.foro;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface HiloRepository extends JpaRepository<Hilo, UUID> {

  interface PuenteResumen {
    UUID getId();

    String getCodigo();

    String getNombre();

    String getRuta();

    String getMunicipio();

    String getDepartamento();

    String getEstadoActual();

    long getCantidadHilos();
  }

  @Query(
      value =
          """
      SELECT p.id, p.codigo, p.nombre, p.ruta, m.nombre AS municipio,
             d.nombre AS departamento, p.estado_actual AS "estadoActual",
             (SELECT COUNT(*) FROM hilo h
              WHERE h.puente_id = p.id AND h.inspeccion_id IS NULL) AS "cantidadHilos"
      FROM puente p JOIN municipio m ON m.codigo = p.municipio_codigo
      JOIN departamento d ON d.codigo = m.departamento_codigo
      WHERE POSITION(LOWER(:busqueda) IN LOWER(CONCAT_WS(' ', p.nombre, p.codigo, p.ruta))) > 0
      ORDER BY p.nombre ASC, p.id ASC
      """,
      countQuery =
          """
      SELECT COUNT(*) FROM puente p
      WHERE POSITION(LOWER(:busqueda) IN LOWER(CONCAT_WS(' ', p.nombre, p.codigo, p.ruta))) > 0
      """,
      nativeQuery = true)
  Page<PuenteResumen> listarPuentesForo(@Param("busqueda") String busqueda, Pageable pageable);

  interface Autor {
    UUID getId();

    String getNombre();
  }

  @Query(value = "SELECT id, nombre FROM usuario WHERE id IN (:ids)", nativeQuery = true)
  List<Autor> buscarNombresAutores(@Param("ids") List<UUID> ids);

  Page<Hilo> findByPuenteIdAndInspeccionIdIsNull(UUID puenteId, Pageable pageable);

  Page<Hilo> findByPuenteIdAndInspeccionIdIsNullAndCategoria(
      UUID puenteId, CategoriaHilo categoria, Pageable pageable);

  Optional<Hilo> findByIdAndInspeccionIdIsNull(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT h FROM Hilo h WHERE h.id = :id AND h.inspeccionId IS NULL")
  Optional<Hilo> buscarParaModificar(@Param("id") UUID id);

  boolean existsByIdAndInspeccionIdIsNull(UUID id);

  // ADR 0010: el módulo base de puentes se valida por SQL nativo sin depender de entidades JPA
  @Query(value = "SELECT COUNT(*) > 0 FROM puente WHERE id = :puenteId", nativeQuery = true)
  boolean existePuente(@Param("puenteId") UUID puenteId);

  @Query(value = "SELECT estado_actual FROM puente WHERE id = :puenteId", nativeQuery = true)
  Optional<String> obtenerEstadoActualPuente(@Param("puenteId") UUID puenteId);

  @Query(value = "SELECT nombre FROM puente WHERE id = :puenteId", nativeQuery = true)
  Optional<String> obtenerNombrePuente(@Param("puenteId") UUID puenteId);

  @Query(value = "SELECT COUNT(*) > 0 FROM usuario WHERE id = :usuarioId", nativeQuery = true)
  boolean existeUsuario(@Param("usuarioId") UUID usuarioId);

  @Query(
      value =
          "SELECT id FROM usuario WHERE estado = 'ACTIVO'"
              + " AND rol IN ('CATEDRATICO', 'ADMINISTRADOR') ORDER BY id ASC",
      nativeQuery = true)
  List<UUID> buscarDestinatariosAlertaPuente();

  @Query(
      value = "SELECT id FROM usuario WHERE id IN (:ids) AND estado = 'ACTIVO'",
      nativeQuery = true)
  List<UUID> filtrarUsuariosActivos(@Param("ids") List<UUID> ids);

  interface InspeccionResumen {
    UUID getId();

    UUID getPuenteId();

    String getEstado();
  }

  @Query(
      value =
          "SELECT id, puente_id AS \"puenteId\", estado FROM inspeccion WHERE id = :inspeccionId",
      nativeQuery = true)
  Optional<InspeccionResumen> buscarInspeccion(@Param("inspeccionId") UUID inspeccionId);

  Optional<Hilo> findByInspeccionId(UUID inspeccionId);
}
