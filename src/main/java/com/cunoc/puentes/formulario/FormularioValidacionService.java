package com.cunoc.puentes.formulario;

import java.util.Map;
import java.util.UUID;

public interface FormularioValidacionService {

  /**
   * Valida autoritativamente el contenido capturado de una inspección contra el esquema de su versión.
   *
   * @param versionId Identificador de la versión del formulario.
   * @param datosPayload Respuestas capturadas en la inspección (JSONB).
   * @throws com.cunoc.puentes.common.NegocioException con estado 422 si la validación falla.
   */
  void validar(UUID versionId, Map<String, Object> datosPayload);

  /**
   * Valida autoritativamente el contenido capturado contra un esquema en memoria.
   *
   * @param esquema Estructura JSON Schema canónica.
   * @param datosPayload Respuestas capturadas en la inspección.
   * @throws com.cunoc.puentes.common.NegocioException con estado 422 si la validación falla.
   */
  void validarConEsquema(Map<String, Object> esquema, Map<String, Object> datosPayload);
}
