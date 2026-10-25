package com.cunoc.puentes.indice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/** El cliente aporta pesos; los elementos permitidos se obtienen exclusivamente en servidor. */
public record CrearJuegoPesosDTO(
    String acta, @NotEmpty List<@NotNull @Valid DefinicionPesoDTO> pesos) {
  public record DefinicionPesoDTO(
      @NotBlank @JsonProperty("elemento_ref") String elementoRef,
      @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("999.999")
          BigDecimal peso) {}
}
