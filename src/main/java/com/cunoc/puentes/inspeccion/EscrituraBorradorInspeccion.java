package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class EscrituraBorradorInspeccion {

  private final InspeccionRepository inspecciones;

  EscrituraBorradorInspeccion(InspeccionRepository inspecciones) {
    this.inspecciones = inspecciones;
  }

  @Transactional
  @Auditable(accion = Acciones.MODIFICAR, entidad = "inspeccion", entidadId = "#inspeccion.id")
  Inspeccion guardar(Inspeccion inspeccion) {
    return inspecciones.save(inspeccion);
  }

  @Transactional
  @Auditable(accion = Acciones.DAR_DE_BAJA, entidad = "inspeccion", entidadId = "#inspeccion.id")
  Inspeccion eliminar(Inspeccion inspeccion) {
    inspeccion.setEliminadoEn(Instant.now());
    return inspecciones.save(inspeccion);
  }
}
