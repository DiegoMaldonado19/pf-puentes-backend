package com.cunoc.puentes.inspeccion;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrato 15: titularidad antes del replay y borrador propio antes de una escritura nueva. 404 si
 * no existe, está eliminada o es de otro autor; 409 al escribir fuera de {@code BORRADOR}.
 */
@Service
public class InspeccionService {

  private final InspeccionRepository inspecciones;

  InspeccionService(InspeccionRepository inspecciones) {
    this.inspecciones = inspecciones;
  }

  @Transactional(readOnly = true)
  public UUID exigirPropia(UUID inspeccionId, UUID usuarioId) {
    return propio(inspeccionId, usuarioId).getPuenteId();
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

  /** Solo M08: la fila lógica original permite autorizar su resultado después de la baja. */
  Inspeccion propioParaReplayDeBaja(UUID inspeccionId, UUID usuarioId) {
    Inspeccion inspeccion =
        inspecciones.findById(inspeccionId).orElseThrow(ConsultaInspeccionService::noEncontrada);
    if (!usuarioId.equals(inspeccion.getAutorId())) {
      throw ConsultaInspeccionService.noEncontrada();
    }
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
