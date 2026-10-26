package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.notificacion.NotificacionService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AvisoBorradoresAbandonadosService {

  static final int DIAS_INACTIVIDAD = 180;
  private static final Logger LOG =
      LoggerFactory.getLogger(AvisoBorradoresAbandonadosService.class);

  private final InspeccionRepository inspecciones;
  private final NotificacionService notificaciones;
  private final EntityManager em;

  AvisoBorradoresAbandonadosService(
      InspeccionRepository inspecciones, NotificacionService notificaciones, EntityManager em) {
    this.inspecciones = inspecciones;
    this.notificaciones = notificaciones;
    this.em = em;
  }

  @Transactional
  int avisar(Instant ahora) {
    Instant corte = ahora.minus(DIAS_INACTIVIDAD, ChronoUnit.DAYS);
    List<Inspeccion> vencidos = inspecciones.findBorradoresInactivosSinAviso(corte);
    int avisados = 0;
    for (Inspeccion inspeccion : vencidos) {
      try {
        notificaciones.notificar(
            inspeccion.getAutorId(),
            "BORRADOR_ABANDONADO",
            "Su borrador de inspección lleva 180 días sin actividad. Revíselo antes de que se purguen sus archivos.",
            "/inspecciones/" + inspeccion.getId());
        em.flush();
        inspecciones.marcarAvisoAbandono(inspeccion.getId(), ahora);
        avisados++;
      } catch (RuntimeException e) {
        LOG.error(
            "No se pudo avisar el abandono del borrador {}; se reintentará en la próxima corrida.",
            inspeccion.getId(),
            e);
      }
    }
    return avisados;
  }
}
