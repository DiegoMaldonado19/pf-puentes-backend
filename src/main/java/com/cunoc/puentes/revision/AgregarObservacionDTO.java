package com.cunoc.puentes.revision;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgregarObservacionDTO(
    @JsonProperty("elemento_ref") @NotBlank @Size(max = 200) String elementoRef,
    @NotBlank @Size(max = 10000) String texto) {}
