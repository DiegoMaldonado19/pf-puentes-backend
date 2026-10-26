package com.cunoc.puentes.mantenimiento;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Interno al módulo; no expone borrado físico ni listados sin paginación (DT-BD-11, DT-BE-08). */
interface OrdenMantenimientoRepository extends Repository<OrdenMantenimiento, UUID> {

  OrdenMantenimiento save(OrdenMantenimiento orden);

  Optional<OrdenMantenimiento> findById(UUID id);

  Optional<OrdenMantenimiento> findByIdAndPuenteId(UUID id, UUID puenteId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from OrdenMantenimiento o where o.id = :ordenId and o.puenteId = :puenteId")
  Optional<OrdenMantenimiento> bloquear(UUID puenteId, UUID ordenId);

  Page<OrdenMantenimiento> findByPuenteId(UUID puenteId, Pageable pageable);

  Page<OrdenMantenimiento> findByPuenteIdAndEstado(
      UUID puenteId, EstadoOrden estado, Pageable pageable);

  Page<OrdenMantenimiento> findByPuenteIdAndTipoAndEstadoIn(
      UUID puenteId, TipoOrden tipo, List<EstadoOrden> estados, Pageable pageable);

  @Query(value = "SELECT EXISTS (SELECT 1 FROM archivo WHERE id = :id)", nativeQuery = true)
  boolean existeArchivo(UUID id);

  // ADR 0010: consultar módulos vecinos por SQL, sin importar sus entidades JPA.
  @Query(value = "SELECT EXISTS (SELECT 1 FROM puente WHERE id = :puenteId)", nativeQuery = true)
  boolean existePuente(UUID puenteId);

  @Query(value = "SELECT puente_id FROM inspeccion WHERE id = :inspeccionId", nativeQuery = true)
  Optional<UUID> buscarPuenteDeInspeccion(UUID inspeccionId);

  @Query(
      "select count(o) > 0 from OrdenMantenimiento o"
          + " where o.inspeccionId = :inspeccionId and o.elementoRef = :elementoRef")
  boolean existePorInspeccionYElemento(UUID inspeccionId, String elementoRef);

  @Query(
      "select count(o) > 0 from OrdenMantenimiento o"
          + " where o.puenteId = :puenteId"
          + " and o.tipo = com.cunoc.puentes.mantenimiento.TipoOrden.RUTINARIO"
          + " and o.estado in (com.cunoc.puentes.mantenimiento.EstadoOrden.PROPUESTA,"
          + " com.cunoc.puentes.mantenimiento.EstadoOrden.PROGRAMADA,"
          + " com.cunoc.puentes.mantenimiento.EstadoOrden.EN_EJECUCION)")
  boolean existeRutinariaAbierta(UUID puenteId);
}
