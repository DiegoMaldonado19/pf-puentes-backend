package com.cunoc.puentes.indice;

import java.util.List;

/** Ruta de propiedades del esquema; la referencia semántica es la clave del mapa de campos. */
public record CampoDeDano(List<String> ruta, boolean repetido) {
  public CampoDeDano {
    ruta = List.copyOf(ruta);
  }
}
