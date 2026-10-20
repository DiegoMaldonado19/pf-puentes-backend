package com.cunoc.puentes.indice;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bean interno separado para que el aspecto audite cada peso dentro de la transacción del juego.
 */
@Service
@PreAuthorize("hasRole('ADMINISTRADOR')")
class CreacionPesoService {

  private final PesoElementoRepository pesos;
  private final PesosMapper mapper;

  CreacionPesoService(PesoElementoRepository pesos, PesosMapper mapper) {
    this.pesos = pesos;
    this.mapper = mapper;
  }

  @Transactional
  @Auditable(accion = Acciones.CREAR, entidad = "peso_elemento", entidadId = "#result.id")
  public PesoElemento crear(PesoConfigurado definicion, VersionPesos version) {
    return pesos.save(mapper.aPeso(definicion, version));
  }
}
