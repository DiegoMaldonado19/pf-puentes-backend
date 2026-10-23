package com.cunoc.puentes.formulario;

import com.cunoc.puentes.common.ErrorCampo;
import com.cunoc.puentes.common.NegocioException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class FormularioValidacionServiceImpl implements FormularioValidacionService {

  private final FormularioVersionRepository repository;
  private final ObjectMapper objectMapper;
  private final JsonSchemaFactory schemaFactory;
  private final Map<UUID, JsonSchema> cacheEsquemas = new ConcurrentHashMap<>();

  public FormularioValidacionServiceImpl(
      FormularioVersionRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
  }

  @Override
  public void validar(UUID versionId, Map<String, Object> datosPayload) {
    JsonSchema schema =
        cacheEsquemas.computeIfAbsent(
            versionId,
            id -> {
              FormularioVersion version =
                  repository
                      .findById(id)
                      .orElseThrow(
                          () ->
                              new NegocioException(
                                  HttpStatus.NOT_FOUND,
                                  "formulario-no-encontrado",
                                  "Versión de formulario no encontrada",
                                  "No existe una versión de formulario con id " + id));
              JsonNode schemaNode = objectMapper.valueToTree(version.getEsquema());
              return schemaFactory.getSchema(schemaNode);
            });

    ejecutarValidacion(schema, datosPayload);
  }

  @Override
  public void validarConEsquema(Map<String, Object> esquema, Map<String, Object> datosPayload) {
    JsonNode schemaNode = objectMapper.valueToTree(esquema);
    JsonSchema schema = schemaFactory.getSchema(schemaNode);
    ejecutarValidacion(schema, datosPayload);
  }

  private void ejecutarValidacion(JsonSchema schema, Map<String, Object> datosPayload) {
    JsonNode dataNode = objectMapper.valueToTree(datosPayload != null ? datosPayload : Map.of());
    Set<ValidationMessage> validationMessages = schema.validate(dataNode);

    if (!validationMessages.isEmpty()) {
      List<ErrorCampo> errores =
          validationMessages.stream()
              .map(
                  msg -> {
                    String ruta = msg.getPath();
                    if (ruta.startsWith("$.") || ruta.startsWith("$[")) {
                      ruta = ruta.substring(2);
                    } else if (ruta.equals("$")) {
                      ruta = "datos";
                    }
                    return new ErrorCampo(ruta, msg.getMessage());
                  })
              .toList();

      throw new NegocioException(
              HttpStatus.UNPROCESSABLE_ENTITY,
              "validacion-esquema",
              "Error de validación contra el formulario",
              "Los datos de la inspección no cumplen con las reglas del formulario correspondiente.")
          .conErrores(errores);
    }
  }

  public void invalidarCache(UUID versionId) {
    cacheEsquemas.remove(versionId);
  }
}
