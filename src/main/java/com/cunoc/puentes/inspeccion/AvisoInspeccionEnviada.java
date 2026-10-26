package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.notificacion.NotificacionService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Avisa al catedrático del curso cuando un estudiante envía. El fallo de la campana se registra y
 * no revierte el {@code ENVIADA}.
 */
@Component
class AvisoInspeccionEnviada {

  static final String TIPO = "INSPECCION_ENVIADA";
  private static final Logger LOG = LoggerFactory.getLogger(AvisoInspeccionEnviada.class);

  private final ReferenciasInspeccion referencias;
  private final NotificacionService notificaciones;

  AvisoInspeccionEnviada(ReferenciasInspeccion referencias, NotificacionService notificaciones) {
    this.referencias = referencias;
    this.notificaciones = notificaciones;
  }

  void avisar(UUID inspeccionId, UUID cursoId) {
    if (cursoId == null) {
      return;
    }
    referencias
        .catedraticoDeCurso(cursoId)
        .ifPresent(
            catedratico -> {
              try {
                notificaciones.notificar(
                    catedratico,
                    TIPO,
                    "Hay una inspección enviada a revisión.",
                    "/inspecciones/" + inspeccionId);
              } catch (RuntimeException e) {
                LOG.error(
                    "No se pudo avisar el envío de la inspección {} ({})",
                    inspeccionId,
                    e.getClass().getSimpleName());
              }
            });
  }
}
