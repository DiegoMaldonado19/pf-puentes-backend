package com.cunoc.puentes.puente;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Catálogo INE de solo lectura; lo carga una migración (ADR 0011). */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Departamento {

  @Id private String codigo;

  private String nombre;
}
