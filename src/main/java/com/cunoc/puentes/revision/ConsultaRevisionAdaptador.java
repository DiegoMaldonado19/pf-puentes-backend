package com.cunoc.puentes.revision;

import com.cunoc.puentes.inspeccion.puerto.ConsultaRevision;
import com.cunoc.puentes.inspeccion.puerto.ResumenRevision;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Implementa el puerto que usan el envío (ADR 0057) y la consulta de inspecciones (B3: distintivo y
 * observaciones pendientes para el autor).
 */
@Component
class ConsultaRevisionAdaptador implements ConsultaRevision {

  private final ObservacionRevisionRepository observaciones;
  private final RevisionRepository revisiones;

  ConsultaRevisionAdaptador(
      ObservacionRevisionRepository observaciones, RevisionRepository revisiones) {
    this.observaciones = observaciones;
    this.revisiones = revisiones;
  }

  @Override
  public int observacionesPendientes(UUID inspeccionId) {
    return Math.toIntExact(observaciones.contarPendientes(inspeccionId));
  }

  @Override
  public Map<UUID, ResumenRevision> resumenes(Collection<UUID> inspeccionIds) {
    if (inspeccionIds.isEmpty()) {
      return Map.of();
    }
    // De la más reciente a la más antigua: la primera de cada inspección es su última emitida
    Map<UUID, Revision> ultimas = new HashMap<>();
    for (Revision revision :
        revisiones.findByInspeccionIdInAndVeredictoIsNotNullOrderByCreadoEnDesc(inspeccionIds)) {
      ultimas.putIfAbsent(revision.getInspeccionId(), revision);
    }
    if (ultimas.isEmpty()) {
      return Map.of();
    }
    List<UUID> revisionIds = ultimas.values().stream().map(Revision::getId).toList();
    Map<UUID, Long> pendientes =
        observaciones.findByRevision_IdInAndResueltaEnIsNull(revisionIds).stream()
            .collect(Collectors.groupingBy(o -> o.getRevision().getId(), Collectors.counting()));
    Map<UUID, ResumenRevision> resumenes = new HashMap<>();
    ultimas.forEach(
        (inspeccionId, revision) ->
            resumenes.put(
                inspeccionId,
                new ResumenRevision(
                    revision.getVeredicto().name(),
                    revision.getEmitidaEn(),
                    Math.toIntExact(pendientes.getOrDefault(revision.getId(), 0L)))));
    return resumenes;
  }
}
