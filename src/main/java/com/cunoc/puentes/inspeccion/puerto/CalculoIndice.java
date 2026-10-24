package com.cunoc.puentes.inspeccion.puerto;

import com.cunoc.puentes.indice.ResultadoIndiceCondicion;
import java.util.Map;
import java.util.UUID;

/**
 * Puerto del contrato 3 hacia P8. El núcleo {@code CalculadoraIndiceCondicion} ya existe; este
 * puerto es el adaptador {@code datos JSON + versión → elementos} que P5 llamará al publicar.
 */
public interface CalculoIndice {

  ResultadoIndiceCondicion calcular(Map<String, Object> datos, UUID formularioVersionId);
}
