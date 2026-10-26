package com.cunoc.puentes.inspeccion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReemplazarAcompanantesDTO(@NotNull List<@Valid AcompananteDTO> acompanantes) {}
