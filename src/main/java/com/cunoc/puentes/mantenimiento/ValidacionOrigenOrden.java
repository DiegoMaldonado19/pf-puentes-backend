package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.common.NegocioException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comprobación interna de RN-MTO-01; la futura fachada debe autorizar al actor antes de llamarla.
 */
@Component
@Transactional(readOnly = true)
class ValidacionOrigenOrden {

  private final OrdenMantenimientoRepository ordenes;

  ValidacionOrigenOrden(OrdenMantenimientoRepository ordenes) {
    this.ordenes = ordenes;
  }

  void validar(UUID puenteId, UUID inspeccionId) {
    if (puenteId == null) {
      throw new IllegalArgumentException("La orden debe identificar un puente.");
    }
    if (!ordenes.existePuente(puenteId)) {
      throw new NegocioException(
          HttpStatus.NOT_FOUND,
          "puente-no-encontrado",
          "Puente no encontrado",
          "El puente indicado para la orden no existe.");
    }
    if (inspeccionId == null) {
      return;
    }
    UUID puenteDeOrigen =
        ordenes
            .buscarPuenteDeInspeccion(inspeccionId)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "inspeccion-no-encontrada",
                        "Inspección no encontrada",
                        "La inspección de origen indicada no existe."));
    if (!puenteId.equals(puenteDeOrigen)) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "origen-de-orden-incompatible",
          "Inspección de otro puente",
          "La inspección de origen debe pertenecer al mismo puente que la orden.");
    }
  }
}
