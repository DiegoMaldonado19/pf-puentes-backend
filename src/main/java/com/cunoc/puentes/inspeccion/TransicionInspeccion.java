package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransicionInspeccion extends EntidadBase {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "inspeccion_id")
  private Inspeccion inspeccion;

  @Enumerated(EnumType.STRING)
  private EstadoInspeccion estadoOrigen;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EstadoInspeccion estadoDestino;

  private UUID actorId;

  private UUID revisionId;

  @Column(length = 500)
  private String motivo;
}
