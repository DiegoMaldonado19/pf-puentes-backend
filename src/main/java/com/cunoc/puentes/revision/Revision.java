package com.cunoc.puentes.revision;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Revision extends EntidadBase {

  private UUID inspeccionId;

  private UUID revisorId;

  @Enumerated(EnumType.STRING)
  private Veredicto veredicto;

  private String observacion;

  private BigDecimal calificacion;

  private Instant emitidaEn;
}
