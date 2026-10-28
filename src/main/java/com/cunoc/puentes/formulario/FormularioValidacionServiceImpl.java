package com.cunoc.puentes.formulario;

import com.cunoc.puentes.common.ErrorCampo;
import com.cunoc.puentes.common.NegocioException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaId;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class FormularioValidacionServiceImpl implements FormularioValidacionService {

  private final FormularioVersionRepository repository;
  private final ObjectMapper objectMapper;
  private final JsonSchemaFactory schemaFactory;
  private final JsonSchema metaesquema;
  private final Map<UUID, JsonSchema> cacheEsquemas = new ConcurrentHashMap<>();

  @Autowired
  public FormularioValidacionServiceImpl(FormularioVersionRepository repository) {
    this(repository, new ObjectMapper());
  }

  public FormularioValidacionServiceImpl(
      FormularioVersionRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    // networknt trae el metaesquema draft-07 en el jar: no hace falta red
    this.metaesquema = schemaFactory.getSchema(SchemaLocation.of(SchemaId.V7));
  }

  @Override
  public void validar(UUID versionId, Map<String, Object> datosPayload) {
    ejecutarValidacion(esquemaDe(versionId), datosPayload);
  }

  @Override
  public void validarConEsquema(Map<String, Object> esquema, Map<String, Object> datosPayload) {
    JsonNode schemaNode = objectMapper.valueToTree(esquema);
    JsonSchema schema = schemaFactory.getSchema(schemaNode);
    ejecutarValidacion(schema, datosPayload);
  }

  @Override
  public List<ErrorCampo> erroresDeContenido(UUID versionId, Map<String, Object> datosPayload) {
    return mensajes(esquemaDe(versionId), datosPayload).stream()
        .filter(mensaje -> !"required".equals(mensaje.getType()))
        .map(FormularioValidacionServiceImpl::error)
        .toList();
  }

  @Override
  public void compilar(Map<String, Object> esquema) {
    Set<ValidationMessage> errores = metaesquema.validate(objectMapper.valueToTree(esquema));
    if (!errores.isEmpty()) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "esquema-invalido",
          "El esquema no es un JSON Schema válido",
          errores.iterator().next().getMessage());
    }
  }

  private JsonSchema esquemaDe(UUID versionId) {
    return cacheEsquemas.computeIfAbsent(
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
  }

  private Set<ValidationMessage> mensajes(JsonSchema schema, Map<String, Object> datosPayload) {
    JsonNode dataNode = objectMapper.valueToTree(datosPayload != null ? datosPayload : Map.of());
    return schema.validate(dataNode);
  }

  private void ejecutarValidacion(JsonSchema schema, Map<String, Object> datosPayload) {
    List<ErrorCampo> errores =
        mensajes(schema, datosPayload).stream()
            .map(FormularioValidacionServiceImpl::error)
            .toList();

    if (!errores.isEmpty()) {
      throw new NegocioException(
              HttpStatus.UNPROCESSABLE_ENTITY,
              "validacion-esquema",
              "Error de validación contra el formulario",
              "Los datos de la inspección no cumplen con las reglas del formulario correspondiente.")
          .conErrores(errores);
    }
  }

  private static ErrorCampo error(ValidationMessage msg) {
    String ruta = msg.getInstanceLocation().toString();
    if (ruta.startsWith("$.") || ruta.startsWith("$[")) {
      ruta = ruta.substring(2);
    } else if (ruta.equals("$") || ruta.isEmpty()) {
      ruta = "datos";
    }
    return new ErrorCampo(ruta, mensaje(msg));
  }

  private static String mensaje(ValidationMessage msg) {
    String tipo = msg.getType();
    if (tipo == null) {
      return msg.getMessage();
    }
    return switch (tipo) {
      case "type" -> "El valor no coincide con el tipo esperado.";
      case "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum" ->
          "El valor está fuera del rango permitido.";
      case "enum" -> "El valor no está entre las opciones permitidas.";
      default -> msg.getMessage();
    };
  }

  public void invalidarCache(UUID versionId) {
    cacheEsquemas.remove(versionId);
  }
}
