package com.cunoc.puentes.indice.dto;

import com.cunoc.puentes.indice.CondicionesCriticas;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Banderas opcionales de condiciones críticas para estimación del IC (RN-IC-05). */
@Schema(description = "Banderas de condiciones críticas estructurales (RN-IC-05)")
public record CondicionesCriticasDTO(
    @JsonProperty("socavacion_con_cimentacion_expuesta_en_estribo_o_pila")
        @Schema(description = "Socavación con exposición de cimentación en estribo o pila.")
        Boolean socavacionConCimentacionExpuestaEnEstriboOPila,
    @JsonProperty("asentamiento_de_estructura") @Schema(description = "Asentamiento de estructura.")
        Boolean asentamientoDeEstructura,
    @JsonProperty("portante_cortado_o_con_perdida_de_seccion")
        @Schema(description = "Elemento portante cortado o con pérdida de sección.")
        Boolean portanteCortadoOConPerdidaDeSeccion,
    @JsonProperty("ausencia_total_de_portante")
        @Schema(description = "Ausencia total de un elemento portante.")
        Boolean ausenciaTotalDePortante) {

  public CondicionesCriticas aDominio() {
    return new CondicionesCriticas(
        Boolean.TRUE.equals(socavacionConCimentacionExpuestaEnEstriboOPila),
        Boolean.TRUE.equals(asentamientoDeEstructura),
        Boolean.TRUE.equals(portanteCortadoOConPerdidaDeSeccion),
        Boolean.TRUE.equals(ausenciaTotalDePortante));
  }
}
