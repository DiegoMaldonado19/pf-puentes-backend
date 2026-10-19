package com.cunoc.puentes.indice;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Definición inmutable de un juego; no persiste ni modifica VersionPesos o el formulario. */
public record JuegoPesos(UUID formularioVersionId, String acta, List<PesoConfigurado> pesos) {

  public JuegoPesos {
    if (formularioVersionId == null) {
      throw new IllegalArgumentException("La versión del formulario es obligatoria.");
    }
    if (acta != null && (acta.isBlank() || acta.codePointCount(0, acta.length()) > 300)) {
      throw new IllegalArgumentException(
          "El acta debe ser una referencia no vacía de hasta 300 caracteres o null para pesos provisionales.");
    }
    if (pesos == null || pesos.isEmpty()) {
      throw new IllegalArgumentException("El juego debe contener al menos un peso.");
    }
    Set<String> referencias = new HashSet<>();
    for (PesoConfigurado peso : pesos) {
      if (peso == null) {
        throw new IllegalArgumentException("Los pesos del juego no pueden ser nulos.");
      }
      if (!referencias.add(peso.elementoRef())) {
        throw new IllegalArgumentException("El juego no puede repetir referencias de elementos.");
      }
    }
    pesos = List.copyOf(pesos);
  }

  /** NULL en version_pesos.acta identifica pesos provisionales, según la migración existente. */
  public boolean esProvisional() {
    return acta == null;
  }
}
