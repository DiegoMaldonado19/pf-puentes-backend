package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TokenRenovacion extends EntidadBase {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Usuario usuario;

  private String tokenHash;

  private Instant expiraEn;

  private Instant revocadoEn;
}
