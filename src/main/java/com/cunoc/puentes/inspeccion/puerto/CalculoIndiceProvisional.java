package com.cunoc.puentes.inspeccion.puerto;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.indice.ResultadoIndiceCondicion;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Stub del contrato 3. No inventa un IC: US-P5-08 conectará {@code CalculadoraIndiceCondicion}
 * cuando existan metadatos de daño y pesos vigentes.
 */
class CalculoIndiceProvisional implements CalculoIndice {

  @Override
  public ResultadoIndiceCondicion calcular(Map<String, Object> datos, UUID formularioVersionId) {
    if (datos == null || formularioVersionId == null) {
      throw new IllegalArgumentException("Los datos y la versión del formulario son obligatorios.");
    }
    throw new NegocioException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "ic-no-calculable",
        "No se puede calcular el índice de condición",
        "El adaptador provisional no calcula el IC: faltan el contrato 3 de P8 y los metadatos de P4.");
  }
}
