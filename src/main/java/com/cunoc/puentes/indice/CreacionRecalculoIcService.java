package com.cunoc.puentes.indice;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Bean interno para auditar cada nuevo recálculo bajo el bloqueo previo de la inspección. */
@Service
@PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
class CreacionRecalculoIcService {

  private final RecalculoIcRepository recalculos;
  private final PesosMapper mapper;

  CreacionRecalculoIcService(RecalculoIcRepository recalculos, PesosMapper mapper) {
    this.recalculos = recalculos;
    this.mapper = mapper;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @Auditable(accion = Acciones.CREAR, entidad = "recalculo_ic", entidadId = "#result.id")
  public RecalculoIc crear(
      UUID inspeccionId,
      VersionPesos version,
      BigDecimal indiceCondicion,
      EstadoCondicion estadoCalculado,
      Anulacion anulacion) {
    return recalculos.save(
        mapper.aRecalculo(inspeccionId, version, indiceCondicion, estadoCalculado, anulacion));
  }
}
