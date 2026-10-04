package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
public class Invitacion extends EntidadBase {

  private String correo;

  @Enumerated(EnumType.STRING)
  private Rol rol;

  private String tokenHash;

  private Instant expiraEn;

  private Instant aceptadaEn;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Usuario administrador;
}
