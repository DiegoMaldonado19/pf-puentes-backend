package com.cunoc.puentes.puente;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import com.cunoc.puentes.inspeccion.InspeccionPublicada;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** RN-INV-09: aplica el resultado de la inspección publicada, auditado como cambio de estado. */
@Service
public class CamposDerivadosService {

  private final PuenteRepository puentes;

  CamposDerivadosService(PuenteRepository puentes) {
    this.puentes = puentes;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @Auditable(accion = Acciones.CAMBIAR_ESTADO, entidad = "puente", entidadId = "#evento.puenteId()")
  public void actualizar(InspeccionPublicada evento) {
    puentes
        .getReferenceById(evento.puenteId())
        .actualizarCondicion(evento.estado(), evento.indiceCondicion(), evento.fecha());
  }
}
