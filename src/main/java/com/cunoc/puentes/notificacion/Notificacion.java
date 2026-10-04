package com.cunoc.puentes.notificacion;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
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
public class Notificacion extends EntidadBase {

  private UUID usuarioId;

  private String tipo;

  private String mensaje;

  private String enlace;

  private Instant leidaEn;
}
