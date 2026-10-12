package com.cunoc.puentes.common.auditoria;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Escribe las filas de la bitácora. Se une a la transacción de la operación auditada. */
@Service
class AuditoriaService {

  private final AuditoriaRepository repositorio;
  private final ContextoAuditoria contexto;

  AuditoriaService(AuditoriaRepository repositorio, ContextoAuditoria contexto) {
    this.repositorio = repositorio;
    this.contexto = contexto;
  }

  void registrar(
      String accion,
      String entidad,
      UUID entidadId,
      Map<String, Object> anterior,
      Map<String, Object> posterior,
      String motivo) {
    Auditoria fila = new Auditoria();
    fila.setUsuarioId(contexto.usuario().orElse(null));
    fila.setAccion(accion);
    fila.setEntidad(entidad);
    fila.setEntidadId(entidadId);
    fila.setValorAnterior(anterior);
    fila.setValorNuevo(posterior);
    fila.setMotivo(motivo);
    fila.setOrigenIp(contexto.origenIp().orElse(null));
    fila.setCorrelacionId(contexto.correlacion());
    // flush: un fallo de la bitácora debe tumbar la operación aquí, no después
    repositorio.saveAndFlush(fila);
  }
}
