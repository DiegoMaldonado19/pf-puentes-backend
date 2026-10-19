package com.cunoc.puentes.mantenimiento;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Interno al módulo; no expone borrado físico ni listados sin paginación (DT-BD-11, DT-BE-08). */
interface OrdenMantenimientoRepository extends Repository<OrdenMantenimiento, UUID> {

  OrdenMantenimiento save(OrdenMantenimiento orden);

  Optional<OrdenMantenimiento> findById(UUID id);

  Page<OrdenMantenimiento> findByPuenteId(UUID puenteId, Pageable pageable);
}
