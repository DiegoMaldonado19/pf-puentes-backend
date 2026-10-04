package com.cunoc.puentes.foro;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
public class Comentario extends EntidadBase {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Hilo hilo;

  private UUID autorId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "respuesta_a_id")
  private Comentario respuestaA;

  private String elementoRef;

  private String texto;

  private Instant editadoEn;

  private Instant eliminadoEn;

  private Instant ocultoEn;

  private String motivoOcultamiento;
}
