package com.cunoc.puentes.common;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** Id UUID v7 (DT-BD-05) y marcas de tiempo en UTC (DT-BD-06, DT-BD-07) de cada entidad. */
@MappedSuperclass
@Getter
public abstract class EntidadBase {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @CreationTimestamp private Instant creadoEn;

  @UpdateTimestamp private Instant actualizadoEn;
}
