package com.cunoc.puentes.indice;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Entrada interna normalizada; MetadatosDanoFormulario la obtiene del esquema persistido de P4. */
public record ElementosDeDano(UUID formularioVersionId, Set<String> referencias) {

  public ElementosDeDano {
    Objects.requireNonNull(formularioVersionId, "La versión de los metadatos es obligatoria.");
    referencias = Set.copyOf(referencias);
  }
}
