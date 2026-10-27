package com.cunoc.puentes.foro.dto;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ResultadoModeracion(
    boolean modificado, UUID entidadId, Instant fecha, String motivo, List<UUID> idsAfectados) {

  public Map<String, Object> valoresAnteriores(String estado) {
    return Map.of(estado, false);
  }

  public Map<String, Object> valoresNuevos(String estado) {
    Map<String, Object> valores = new LinkedHashMap<>();
    valores.put(estado, true);
    valores.put(estado + "_en", fecha.toString());
    valores.put("oculto".equals(estado) ? "motivo_ocultamiento" : "motivo_cierre", motivo);
    if ("oculto".equals(estado)) {
      valores.put("ids_ocultados", idsAfectados.stream().map(UUID::toString).toList());
    }
    return valores;
  }
}
