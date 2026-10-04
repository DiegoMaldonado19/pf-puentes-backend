package com.cunoc.puentes.foro;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
public class Hilo extends EntidadBase {

  private UUID puenteId;

  private UUID inspeccionId;

  private UUID autorId;

  private String titulo;

  @Enumerated(EnumType.STRING)
  private CategoriaHilo categoria;

  private Instant cerradoEn;

  private String motivoCierre;
}
