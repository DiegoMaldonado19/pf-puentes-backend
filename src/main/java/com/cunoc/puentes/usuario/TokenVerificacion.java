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

/** Token de un solo uso enviado por correo: verifica el correo o recupera la contraseña. */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TokenVerificacion extends EntidadBase {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Usuario usuario;

  @Enumerated(EnumType.STRING)
  private TipoTokenVerificacion tipo;

  /** SHA-256 del token del enlace; el valor sin hash nunca se guarda (DT-SEC-12). */
  private String tokenHash;

  private Instant expiraEn;

  private Instant usadoEn;
}
