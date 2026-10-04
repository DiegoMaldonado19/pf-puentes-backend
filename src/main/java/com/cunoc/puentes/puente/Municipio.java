package com.cunoc.puentes.puente;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Catálogo INE de solo lectura; lo carga una migración (ADR 0011). */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Municipio {

  @Id private String codigo;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Departamento departamento;

  private String nombre;
}
