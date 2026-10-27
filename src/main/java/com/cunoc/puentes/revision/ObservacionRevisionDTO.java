package com.cunoc.puentes.revision;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record ObservacionRevisionDTO(
    UUID id, @JsonProperty("elemento_ref") String elementoRef, String texto) {
  static ObservacionRevisionDTO de(ObservacionAnclada fila) {
    return new ObservacionRevisionDTO(fila.getId(), fila.getElementoRef(), fila.getTexto());
  }
}
