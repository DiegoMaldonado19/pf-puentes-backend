package com.cunoc.puentes.curso.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record InscribirEstudianteDTO(@NotNull @JsonProperty("estudiante_id") UUID estudianteId) {}
