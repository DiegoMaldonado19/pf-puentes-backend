package com.cunoc.puentes.inspeccion;

import java.math.BigDecimal;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** RN-INS-12: distancia en metros reales (geography). El umbral advierte; no bloquea. */
@Component
class DistanciaAlPuente {

  private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);
  private static final String CONSULTA =
      """
      SELECT round(ST_Distance(ubicacion, ST_SetSRID(ST_MakePoint(:longitud, :latitud), 4326)::geography)::NUMERIC, 2)
      FROM puente WHERE id = :id
      """;

  private final JdbcClient jdbc;
  private final BigDecimal umbral;

  DistanciaAlPuente(
      JdbcClient jdbc, @Value("${sgp.inspeccion.advertencia-gps-metros:500}") int umbralMetros) {
    this.jdbc = jdbc;
    this.umbral = BigDecimal.valueOf(umbralMetros);
  }

  static Point punto(double latitud, double longitud) {
    return WGS84.createPoint(new Coordinate(longitud, latitud));
  }

  BigDecimal medir(UUID puenteId, double latitud, double longitud) {
    return jdbc.sql(CONSULTA)
        .param("id", puenteId)
        .param("latitud", latitud)
        .param("longitud", longitud)
        .query(BigDecimal.class)
        .optional()
        .orElse(null);
  }

  boolean lejana(BigDecimal metros) {
    return metros != null && metros.compareTo(umbral) > 0;
  }

  BigDecimal umbral() {
    return umbral;
  }

  String advertencia(BigDecimal metros) {
    return "La ubicación está a %s m del puente (límite %s m)."
        .formatted(entero(metros), entero(umbral));
  }

  static String entero(BigDecimal metros) {
    return metros.stripTrailingZeros().toPlainString();
  }
}
