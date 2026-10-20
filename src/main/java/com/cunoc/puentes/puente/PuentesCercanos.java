package com.cunoc.puentes.puente;

import com.cunoc.puentes.puente.dto.PuenteCercanoDTO;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** RN-INV-06, DT-BD-04: ST_DWithin sobre geography (metros reales) usa el índice GIST. */
@Component
class PuentesCercanos {

  static final double RADIO_METROS = 100;

  private static final String CONSULTA =
      """
      SELECT id, codigo, nombre, round(ST_Distance(ubicacion, punto)::NUMERIC, 1) AS distancia_metros
      FROM puente, (SELECT ST_SetSRID(ST_MakePoint(:longitud, :latitud), 4326)::geography AS punto) p
      WHERE activo
        AND ST_DWithin(ubicacion, punto, :radio)
        AND (CAST(:excluir AS UUID) IS NULL OR id <> CAST(:excluir AS UUID))
      ORDER BY distancia_metros, codigo
      LIMIT 100
      """;

  private final JdbcClient jdbc;

  PuentesCercanos(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** {@code excluir}: el puente que se edita, para que no se detecte a sí mismo. */
  List<PuenteCercanoDTO> buscar(double latitud, double longitud, UUID excluir) {
    return jdbc.sql(CONSULTA)
        .param("latitud", latitud)
        .param("longitud", longitud)
        .param("radio", RADIO_METROS)
        .param("excluir", excluir)
        .query(PuenteCercanoDTO.class)
        .list();
  }
}
