package com.cunoc.puentes.common;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** La bitácora solo se inserta (RN-USR-09); la consulta llega con la historia de la bitácora. */
interface AuditoriaRepository extends JpaRepository<Auditoria, UUID> {}
