package com.cunoc.puentes.revision;

import com.cunoc.puentes.notificacion.NotificacionService;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvisoRevisionService {
  private final JdbcClient jdbc;
  private final NotificacionService notificaciones;

  AvisoRevisionService(JdbcClient jdbc, NotificacionService notificaciones) {
    this.jdbc = jdbc;
    this.notificaciones = notificaciones;
  }

  @Transactional
  public int avisar(Instant ahora) {
    Instant corte = ahora.atZone(ZoneId.of("America/Guatemala")).minusDays(15).toInstant();
    var candidatos =
        jdbc.sql(
                """
        select i.id, i.enviada_en, c.catedratico_id
        from inspeccion i join curso c on c.id = i.curso_id
        join usuario docente on docente.id = c.catedratico_id
        join usuario autor on autor.id = i.autor_id
        where i.estado in ('ENVIADA', 'EN_REVISION') and i.eliminado_en is null
          and i.enviada_en <= :corte and i.autor_id <> c.catedratico_id
          and autor.rol = 'ESTUDIANTE' and docente.rol = 'CATEDRATICO' and docente.estado = 'ACTIVO'
          and exists (select 1 from inscripcion m where m.curso_id = c.id and m.estudiante_id = i.autor_id)
          and not exists (select 1 from aviso_revision a where a.inspeccion_id = i.id and a.enviada_en = i.enviada_en and a.destinatario_id = c.catedratico_id)
        order by i.enviada_en, i.id for update of i
        """)
            .param("corte", java.sql.Timestamp.from(corte))
            .query(Vencida.class)
            .list();
    int enviados = 0;
    for (var fila : candidatos) {
      int nuevo =
          jdbc.sql(
                  "insert into aviso_revision (inspeccion_id, enviada_en, destinatario_id) values (:id, :envio, :docente) on conflict do nothing")
              .param("id", fila.id())
              .param("envio", java.sql.Timestamp.from(fila.enviadaEn()))
              .param("docente", fila.catedraticoId())
              .update();
      if (nuevo == 1) {
        notificaciones.notificar(
            fila.catedraticoId(),
            "REVISION_VENCIDA",
            "Una inspección de tus estudiantes lleva al menos 15 días naturales sin veredicto. Revisa la bandeja académica.",
            "/revisiones");
        enviados++;
      }
    }
    return enviados;
  }

  record Vencida(UUID id, Instant enviadaEn, UUID catedraticoId) {}
}
