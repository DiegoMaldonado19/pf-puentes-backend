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
import java.util.HashMap;
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
  private final Map<UUID, Esquema> cacheEsquemas = new ConcurrentHashMap<>();

  /** Esquema compilado con las etiquetas de sus campos para los mensajes en español (B2). */
  private record Esquema(JsonSchema schema, Map<String, String> titulos) {}

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
    ejecutarValidacion(preparar(esquema), datosPayload);
  }

  @Override
  public List<ErrorCampo> erroresDeContenido(UUID versionId, Map<String, Object> datosPayload) {
    Esquema esquema = esquemaDe(versionId);
    return mensajes(esquema.schema(), datosPayload).stream()
        .filter(mensaje -> !"required".equals(mensaje.getType()))
        .map(mensaje -> error(mensaje, esquema.titulos()))
        .toList();
  }

  @Override
  public List<ErrorCampo> erroresCompletos(UUID versionId, Map<String, Object> datosPayload) {
    Esquema esquema = esquemaDe(versionId);
    return mensajes(esquema.schema(), datosPayload).stream()
        .map(mensaje -> error(mensaje, esquema.titulos()))
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

  private Esquema esquemaDe(UUID versionId) {
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
          return preparar(version.getEsquema());
        });
  }

  private Esquema preparar(Map<String, Object> esquema) {
    JsonNode schemaNode = objectMapper.valueToTree(esquema);
    return new Esquema(schemaFactory.getSchema(schemaNode), titulos(esquema));
  }

  private Set<ValidationMessage> mensajes(JsonSchema schema, Map<String, Object> datosPayload) {
    JsonNode dataNode = objectMapper.valueToTree(datosPayload != null ? datosPayload : Map.of());
    return schema.validate(dataNode);
  }

  private void ejecutarValidacion(Esquema esquema, Map<String, Object> datosPayload) {
    List<ErrorCampo> errores =
        mensajes(esquema.schema(), datosPayload).stream()
            .map(mensaje -> error(mensaje, esquema.titulos()))
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

  /**
   * Etiqueta visible por ruta: la sección ("datos_generales") y cada campo
   * ("datos_generales.longitud_total"), tomadas de {@code title}.
   */
  static Map<String, String> titulos(Map<String, Object> esquema) {
    Map<String, String> titulos = new HashMap<>();
    for (Map.Entry<String, Object> seccion : propiedades(esquema).entrySet()) {
      Map<String, Object> nodo = mapa(seccion.getValue());
      titulos.put(seccion.getKey(), etiqueta(nodo, seccion.getKey()));
      propiedades(nodo)
          .forEach(
              (campo, definicion) ->
                  titulos.put(seccion.getKey() + "." + campo, etiqueta(mapa(definicion), campo)));
    }
    return titulos;
  }

  private static ErrorCampo error(ValidationMessage msg, Map<String, String> titulos) {
    String ruta = rutaDe(msg);
    String etiqueta =
        titulos.getOrDefault(
            ruta.replaceAll("\\[\\d+]", ""), ruta.substring(ruta.lastIndexOf('.') + 1));
    return new ErrorCampo(ruta, mensaje(msg, etiqueta));
  }

  private static String rutaDe(ValidationMessage msg) {
    String ruta = msg.getInstanceLocation().toString();
    if (ruta.startsWith("$.") || ruta.startsWith("$[")) {
      ruta = ruta.substring(2);
    } else if (ruta.equals("$") || ruta.isEmpty()) {
      ruta = "datos";
    }
    if ("required".equals(msg.getType()) && msg.getArguments().length > 0) {
      String propiedad = String.valueOf(msg.getArguments()[0]);
      ruta = "datos".equals(ruta) ? propiedad : ruta + "." + propiedad;
    }
    return ruta;
  }

  /**
   * Mensajes en español que nombran el campo y cuentan qué pasa, p. ej. "Longitud Total del Puente
   * (m) es obligatorio." (B2).
   */
  private static String mensaje(ValidationMessage msg, String etiqueta) {
    String tipo = msg.getType();
    String detalle =
        tipo == null
            ? "no es válido."
            : switch (tipo) {
              case "required" -> "es obligatorio.";
              case "type" -> "no coincide con el tipo esperado.";
              case "minimum", "exclusiveMinimum" -> "está por debajo del mínimo permitido.";
              case "maximum", "exclusiveMaximum" -> "supera el máximo permitido.";
              case "enum" -> "no está entre las opciones permitidas.";
              case "maxLength" -> "supera la longitud máxima permitida.";
              case "minLength" -> "es más corto de lo permitido.";
              case "format", "pattern" -> "no tiene un formato válido.";
              default -> "no es válido.";
            };
    return etiqueta + " " + detalle;
  }

  private static String etiqueta(Map<String, Object> nodo, String respaldo) {
    return nodo.get("title") instanceof String titulo && !titulo.isBlank() ? titulo : respaldo;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> propiedades(Map<String, Object> nodo) {
    Object props = nodo.get("properties");
    return props instanceof Map<?, ?> ? (Map<String, Object>) props : Map.of();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapa(Object valor) {
    return valor instanceof Map<?, ?> ? (Map<String, Object>) valor : Map.of();
  }

  public void invalidarCache(UUID versionId) {
    cacheEsquemas.remove(versionId);
  }
}
