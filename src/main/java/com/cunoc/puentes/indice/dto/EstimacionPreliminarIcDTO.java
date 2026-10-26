package com.cunoc.puentes.indice.dto;

import com.cunoc.puentes.indice.Anulacion;
import com.cunoc.puentes.indice.EstadoCondicion;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Estimación preliminar del IC para borradores de inspección (RN-IC-07, DT-ARQ-06).
 *
 * <p>Identifica explícitamente el resultado como preliminar y no oficial; no actualiza la condición
 * vigente del inventario de puentes ni reemplaza el cálculo oficial realizado al publicar.
 */
@Schema(description = "Estimación preliminar del IC para borradores de inspección (RN-IC-07)")
public record EstimacionPreliminarIcDTO(
    @JsonProperty("indice_condicion")
        @Schema(description = "Índice de condición estimado (0 a 100), redondeado a dos decimales.")
        BigDecimal indiceCondicion,
    @JsonProperty("estado_calculado")
        @Schema(description = "Estado de condición resultante del cálculo.")
        EstadoCondicion estadoCalculado,
    @JsonProperty("anulaciones")
        @Schema(description = "Causas de anulación crítica detectadas (RN-IC-05).")
        Set<Anulacion> anulaciones,
    @JsonProperty("version_pesos_id")
        @Schema(description = "Juego de pesos utilizado para la estimación.")
        UUID versionPesosId,
    @JsonProperty("preliminar")
        @Schema(
            description = "Siempre verdadero para indicar que es un valor preliminar no oficial.")
        boolean preliminar,
    @JsonProperty("rotulo") @Schema(description = "Rótulo textual obligatorio según RN-IC-07.")
        String rotulo,
    @JsonProperty("advertencia")
        @Schema(
            description =
                "Advertencia indicando que el cálculo no afecta la condición oficial del puente.")
        String advertencia) {

  public static final String ROTULO_PRELIMINAR = "IC PRELIMINAR";
  public static final String ADVERTENCIA_PRELIMINAR =
      "Estimación preliminar para borrador de inspección (RN-IC-07). "
          + "No constituye resultado oficial ni actualiza la condición del puente en el inventario.";

  public static EstimacionPreliminarIcDTO de(
      BigDecimal indiceCondicion,
      EstadoCondicion estadoCalculado,
      Set<Anulacion> anulaciones,
      UUID versionPesosId) {
    return new EstimacionPreliminarIcDTO(
        indiceCondicion,
        estadoCalculado,
        anulaciones != null ? Set.copyOf(anulaciones) : Set.of(),
        versionPesosId,
        true,
        ROTULO_PRELIMINAR,
        ADVERTENCIA_PRELIMINAR);
  }
}
