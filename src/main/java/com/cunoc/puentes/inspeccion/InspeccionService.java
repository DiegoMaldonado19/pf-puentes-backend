package com.cunoc.puentes.inspeccion;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrato 15: P1 y P6 exigen un borrador propio antes de tocar archivos o sincronizar. 404 si no
 * existe, está eliminada o es de otro autor; 409 si no está en {@code BORRADOR}.
 */
@Service
public class InspeccionService {

  private final InspeccionRepository inspecciones;

  InspeccionService(InspeccionRepository inspecciones) {
    this.inspecciones = inspecciones;
  }

  @Transactional(readOnly = true)
  public UUID exigirBorradorPropio(UUID inspeccionId, UUID usuarioId) {
    return propioEnBorrador(inspeccionId, usuarioId).getPuenteId();
  }

  @Transactional
  public void registrarActividadAutor(UUID inspeccionId) {
    if (inspeccionId == null) {
      return;
    }
    inspecciones.marcarActividadAutor(inspeccionId, Instant.now());
  }

  Inspeccion propioEnBorrador(UUID inspeccionId, UUID usuarioId) {
    Inspeccion inspeccion = propio(inspeccionId, usuarioId);
    inspeccion.exigirEditable();
    return inspeccion;
  }

  Inspeccion propio(UUID inspeccionId, UUID usuarioId) {
    Inspeccion inspeccion =
        inspecciones
            .findByIdAndEliminadoEnIsNull(inspeccionId)
            .orElseThrow(ConsultaInspeccionService::noEncontrada);
    if (!usuarioId.equals(inspeccion.getAutorId())) {
      throw ConsultaInspeccionService.noEncontrada();
    }
    return inspeccion;
  }
}
