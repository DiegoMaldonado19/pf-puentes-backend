package com.cunoc.puentes.indice;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Entrada interna normalizada; el adaptador de P4 deberá obtenerla de metadatos confiables. */
public record ElementosDeDano(UUID formularioVersionId, Set<String> referencias) {

  public ElementosDeDano {
    Objects.requireNonNull(formularioVersionId, "La versión de los metadatos es obligatoria.");
    referencias = Set.copyOf(referencias);
  }
}
