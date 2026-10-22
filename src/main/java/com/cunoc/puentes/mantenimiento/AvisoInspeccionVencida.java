package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Marca interna de envío: no contiene ni sustituye la notificación de P9. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AvisoInspeccionVencida extends EntidadBase {
  private UUID puenteId;
  private UUID usuarioId;
  private UUID inspeccionId;
  private Instant referenciaEn;

  AvisoInspeccionVencida(UUID puenteId, UUID usuarioId, UUID inspeccionId, Instant referenciaEn) {
    this.puenteId = puenteId;
    this.usuarioId = usuarioId;
    this.inspeccionId = inspeccionId;
    this.referenciaEn = referenciaEn;
  }
}
