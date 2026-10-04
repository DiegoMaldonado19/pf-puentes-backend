package com.cunoc.puentes.curso;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
public class Asignacion extends EntidadBase {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Inscripcion inscripcion;

  private UUID puenteId;
}
