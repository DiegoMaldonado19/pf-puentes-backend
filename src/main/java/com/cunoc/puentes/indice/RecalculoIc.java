package com.cunoc.puentes.indice;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecalculoIc extends EntidadBase {

  private UUID inspeccionId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private VersionPesos versionPesos;

  private BigDecimal indiceCondicion;

  @Enumerated(EnumType.STRING)
  private EstadoCondicion estadoCalculado;

  @Enumerated(EnumType.STRING)
  private Anulacion anulacion;
}
