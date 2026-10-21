package com.cunoc.puentes.common.auditoria;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** La bitácora solo se inserta y se consulta (RN-USR-09); los filtros son opcionales. */
interface AuditoriaRepository
    extends JpaRepository<Auditoria, UUID>, JpaSpecificationExecutor<Auditoria> {}
