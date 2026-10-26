package com.cunoc.puentes.indice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Solicitud de recálculo explícito bajo un juego de pesos de la misma versión de formulario. */
public record SolicitarRecalculoIcDTO(
    @NotNull @JsonProperty("version_pesos_id") UUID versionPesosId) {}
