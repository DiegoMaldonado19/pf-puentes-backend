package com.cunoc.puentes.indice;

import com.cunoc.puentes.common.NegocioException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador interno P4 → P8. El consumidor autoriza la operación; no habilita una API de lectura.
 */
@Component
public class MetadatosDanoFormulario {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json = new ObjectMapper();
  private final ExtractorElementosDeDano extractor = new ExtractorElementosDeDano();

  MetadatosDanoFormulario(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Sin caché: un borrador puede cambiar; una versión histórica se lee por su propio UUID. */
  @Transactional(readOnly = true)
  public ElementosDeDano obtener(UUID version) {
    if (version == null) throw new IllegalArgumentException("La versión es obligatoria.");
    String esquema =
        jdbc
            .query(
                "SELECT esquema::text FROM formulario_version WHERE id = ?",
                (rs, numero) -> rs.getString(1),
                version)
            .stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "formulario-no-encontrado",
                        "Formulario no encontrado",
                        "La versión del formulario indicada no existe."));
    try {
      Map<String, Object> definicion = json.readValue(esquema, new TypeReference<>() {});
      return extractor.extraer(version, definicion);
    } catch (JsonProcessingException error) {
      throw ExtractorElementosDeDano.invalido(
          "$", "El esquema persistido debe ser un objeto JSON.");
    }
  }
}
