package com.cunoc.puentes.foro;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CategoriaHilo {
  DISCUSION_TECNICA("discusion_tecnica"),
  PROPUESTA_INTERVENCION("propuesta_intervencion"),
  CONSULTA("consulta"),
  REFERENCIA_DOCUMENTAL("referencia_documental"),
  ALERTA("alerta");

  private final String codigo;

  CategoriaHilo(String codigo) {
    this.codigo = codigo;
  }

  @JsonValue
  public String getCodigo() {
    return codigo;
  }

  @JsonCreator
  public static CategoriaHilo desdeCodigo(String valor) {
    if (valor == null) {
      return null;
    }
    String normalizado = valor.trim().toLowerCase();
    for (CategoriaHilo cat : values()) {
      if (cat.codigo.equals(normalizado) || cat.name().equalsIgnoreCase(normalizado)) {
        return cat;
      }
    }
    throw new IllegalArgumentException("Categoría de hilo no válida: " + valor);
  }
}
