package com.cunoc.puentes.inspeccion.puerto;

import com.cunoc.puentes.indice.Anulacion;
import com.cunoc.puentes.indice.EstadoCondicion;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Puerto del contrato 3 hacia P8. El núcleo {@code CalculadoraIndiceCondicion} ya existe; este
 * puerto adapta {@code datos JSON + versión → elementos y pesos vigentes}.
 */
public interface CalculoIndice {

  ResultadoCalculo calcular(Map<String, Object> datos, UUID formularioVersionId);

  record ResultadoCalculo(
      BigDecimal indiceCondicion,
      EstadoCondicion estadoCalculado,
      Anulacion anulacion,
      UUID versionPesosId) {}
}
