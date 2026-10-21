package com.cunoc.puentes.puente;

import com.cunoc.puentes.common.NegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * RN-INV-02, RN-INV-03: GT-DD-MMMM-NNNN con correlativo por municipio. El UPSERT bloquea la fila
 * del municipio hasta el commit, así que dos altas simultáneas nunca reciben el mismo número.
 */
@Component
class GeneradorCodigoPuente {

  static final int CORRELATIVO_MAXIMO = 9999;

  private static final String RESERVAR =
      """
      INSERT INTO correlativo_puente (municipio_codigo, ultimo) VALUES (:municipio, 1)
      ON CONFLICT (municipio_codigo) DO UPDATE SET ultimo = correlativo_puente.ultimo + 1
        WHERE correlativo_puente.ultimo < :maximo
      RETURNING ultimo
      """;

  private final JdbcClient jdbc;

  GeneradorCodigoPuente(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  // MANDATORY: si el alta falla, el rollback devuelve el número y nunca se asignó a nadie
  @Transactional(propagation = Propagation.MANDATORY)
  String siguiente(Municipio municipio) {
    int correlativo =
        jdbc.sql(RESERVAR)
            .param("municipio", municipio.getCodigo())
            .param("maximo", CORRELATIVO_MAXIMO)
            .query(Integer.class)
            .optional()
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.CONFLICT,
                        "correlativo-agotado",
                        "Correlativo agotado",
                        "El municipio ya usó los 9999 códigos de puente disponibles."));
    return "GT-%s-%s-%04d"
        .formatted(municipio.getDepartamento().getCodigo(), municipio.getCodigo(), correlativo);
  }
}
