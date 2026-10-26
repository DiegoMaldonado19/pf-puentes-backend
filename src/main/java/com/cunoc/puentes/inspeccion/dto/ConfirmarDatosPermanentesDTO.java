package com.cunoc.puentes.inspeccion.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record ConfirmarDatosPermanentesDTO(@NotNull Map<String, Object> datos) {}
