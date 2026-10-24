package com.cunoc.puentes.inspeccion.puerto;

import java.util.Objects;
import java.util.Set;

/** Grupo de daño del esquema (RN-INS-11). Las rutas las marca P4 cuando entregue el contrato 1. */
public record GrupoElemento(String id, Set<String> rutasDano) {

  public GrupoElemento {
    Objects.requireNonNull(id, "El id del grupo es obligatorio.");
    rutasDano = Set.copyOf(rutasDano == null ? Set.of() : rutasDano);
  }
}
