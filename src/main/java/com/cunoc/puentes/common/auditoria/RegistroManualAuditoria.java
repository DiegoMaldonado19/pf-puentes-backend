package com.cunoc.puentes.common.auditoria;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Registra en la bitácora una acción que no corresponde a una entidad JPA, como la ejecución de un
 * proceso programado (B10). La anotación {@link Auditable} exige una entidad existente; aquí la
 * fila se escribe directamente, con la misma forma y el mismo contexto de correlación.
 */
@Component
public class RegistroManualAuditoria {

  private final AuditoriaService auditoria;

  public RegistroManualAuditoria(AuditoriaService auditoria) {
    this.auditoria = auditoria;
  }

  public void registrar(String accion, String entidad, UUID entidadId, String motivo) {
    auditoria.registrar(accion, entidad, entidadId, null, null, motivo);
  }
}
