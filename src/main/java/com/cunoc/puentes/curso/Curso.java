package com.cunoc.puentes.curso;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Curso extends EntidadBase {

  private String nombre;

  private int anio;

  private int semestre;

  private UUID catedraticoId;

  private boolean vigente = true;
}
