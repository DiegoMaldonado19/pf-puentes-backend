package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.usuario.Rol;
import java.util.UUID;

/**
 * Autorización por fila (DT-SEC-04): si el actor no debe ver la inspección, el servicio responde
 * 404. El Administrador ve publicadas, no borradores. El acompañante no gana visibilidad.
 */
final class VisibilidadInspeccion {

  private VisibilidadInspeccion() {}

  static boolean visible(Inspeccion inspeccion, UUID actorId, Rol rol, UUID catedraticoDelCurso) {
    if (inspeccion == null
        || actorId == null
        || rol == null
        || inspeccion.getEliminadoEn() != null) {
      return false;
    }
    if (inspeccion.getEstado() == EstadoInspeccion.PUBLICADA) {
      return true;
    }
    if (actorId.equals(inspeccion.getAutorId())) {
      return true;
    }
    return rol == Rol.CATEDRATICO
        && inspeccion.getCursoId() != null
        && actorId.equals(catedraticoDelCurso);
  }
}
