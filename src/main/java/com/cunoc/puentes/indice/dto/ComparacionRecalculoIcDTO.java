package com.cunoc.puentes.indice.dto;

/** Vista comparativa entre el resultado publicado original y sus recálculos fechados (RN-IC-08). */
public record ComparacionRecalculoIcDTO(
    ResultadoOriginalIcDTO original, PaginaPesosDTO<RecalculoIcDTO> recalculos) {}

