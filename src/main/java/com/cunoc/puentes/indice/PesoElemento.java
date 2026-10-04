package com.cunoc.puentes.indice;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PesoElemento extends EntidadBase {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private VersionPesos versionPesos;

  private String elementoRef;

  private BigDecimal peso;
}
