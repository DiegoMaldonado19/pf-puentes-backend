package com.cunoc.puentes.inspeccion.puerto;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Vista mínima de una versión de formulario. No expone la entidad de P4 (ADR 0010). */
public record VersionFormularioVista(UUID id, String codigo, Map<String, Object> esquema) {

  public VersionFormularioVista {
    Objects.requireNonNull(id, "El id de la versión es obligatorio.");
    Objects.requireNonNull(codigo, "El código de la versión es obligatorio.");
    esquema = Map.copyOf(esquema == null ? Map.of() : esquema);
  }
}
