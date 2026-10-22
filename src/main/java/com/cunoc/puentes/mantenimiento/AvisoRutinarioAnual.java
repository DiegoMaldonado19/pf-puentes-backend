package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Marca de envío por destinatario y fecha de referencia del período anual. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AvisoRutinarioAnual extends EntidadBase {
  private UUID puenteId;
  private UUID usuarioId;
  private LocalDate fechaReferencia;

  AvisoRutinarioAnual(UUID puenteId, UUID usuarioId, LocalDate fechaReferencia) {
    this.puenteId = puenteId;
    this.usuarioId = usuarioId;
    this.fechaReferencia = fechaReferencia;
  }
}
