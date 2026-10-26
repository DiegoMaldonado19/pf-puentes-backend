package com.cunoc.puentes.indice;

import com.cunoc.puentes.common.NegocioException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detecta las cuatro condiciones críticas de RN-IC-05 a partir de los marcadores {@code
 * x-anulacion-critica} del esquema JSON de P4 y las respuestas de la inspección.
 */
@Component
public class DetectorCondicionesCriticas {

  /**
   * Umbral de calificación (en escala 0 a 4) a partir del cual un campo con {@code
   * x-anulacion-critica} activa la anulación a estado MALO (RN-IC-05). En el formulario SIECA
   * V1.0.0 la calificación 3 corresponde a daño severo / exposición de cimentación.
   */
  public static final int UMBRAL_CALIFICACION_CRITICA = 3;

  private final JdbcTemplate jdbc;
  private final ObjectMapper json = new ObjectMapper();

  public DetectorCondicionesCriticas(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Evalúa las respuestas frente al esquema persistido de la versión del formulario y devuelve las
   * banderas normalizadas de {@link CondicionesCriticas}.
   */
  @Transactional(readOnly = true)
  public CondicionesCriticas detectar(UUID formularioVersionId, Map<String, Object> datos) {
    if (formularioVersionId == null || datos == null) {
      throw new IllegalArgumentException(
          "La versión del formulario y las respuestas son obligatorias.");
    }
    Map<String, Object> esquema = obtenerEsquema(formularioVersionId);
    return detectarDesdeEsquema(esquema, datos);
  }

  /**
   * Devuelve el conjunto de {@code x-elemento-ref} que tienen declarada una {@code
   * x-anulacion-critica} en el esquema de la versión indicada y cuya calificación alcanza o supera
   * el umbral crítico.
   */
  @Transactional(readOnly = true)
  public Set<String> referenciasCriticasActivas(
      UUID formularioVersionId, Map<String, Object> datos) {
    if (formularioVersionId == null || datos == null) {
      throw new IllegalArgumentException(
          "La versión del formulario y las respuestas son obligatorias.");
    }
    Map<String, Object> esquema = obtenerEsquema(formularioVersionId);
    Map<String, CampoCritico> criticos = extraerCamposCriticos(esquema);
    Set<String> activas = new HashSet<>();
    for (var entrada : criticos.entrySet()) {
      int calificacion = leerCalificacion(entrada.getValue().ruta(), datos);
      if (calificacion >= UMBRAL_CALIFICACION_CRITICA) {
        activas.add(entrada.getKey());
      }
    }
    return Set.copyOf(activas);
  }

  /** Evalúa en memoria un esquema JSON ya leído y un mapa de respuestas. */
  public CondicionesCriticas detectarDesdeEsquema(
      Map<String, Object> esquema, Map<String, Object> datos) {
    if (esquema == null || datos == null) {
      throw new IllegalArgumentException("El esquema y las respuestas son obligatorios.");
    }
    Map<String, CampoCritico> criticos = extraerCamposCriticos(esquema);
    boolean socavacion = false;
    boolean asentamiento = false;
    boolean perdidaSeccion = false;
    boolean ausenciaPortante = false;

    for (CampoCritico campo : criticos.values()) {
      int calificacion = leerCalificacion(campo.ruta(), datos);
      if (calificacion >= UMBRAL_CALIFICACION_CRITICA) {
        switch (campo.anulacion()) {
          case SOCAVACION -> socavacion = true;
          case ASENTAMIENTO -> asentamiento = true;
          case PERDIDA_SECCION -> perdidaSeccion = true;
          case AUSENCIA_PORTANTE -> ausenciaPortante = true;
        }
      }
    }
    return new CondicionesCriticas(socavacion, asentamiento, perdidaSeccion, ausenciaPortante);
  }

  /** Combina mediante disyunción lógica dos conjuntos de banderas críticas. */
  public static CondicionesCriticas combinar(CondicionesCriticas a, CondicionesCriticas b) {
    if (a == null && b == null) {
      return new CondicionesCriticas(false, false, false, false);
    }
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return new CondicionesCriticas(
        a.socavacionConCimentacionExpuestaEnEstriboOPila()
            || b.socavacionConCimentacionExpuestaEnEstriboOPila(),
        a.asentamientoDeEstructura() || b.asentamientoDeEstructura(),
        a.portanteCortadoOConPerdidaDeSeccion() || b.portanteCortadoOConPerdidaDeSeccion(),
        a.ausenciaTotalDePortante() || b.ausenciaTotalDePortante());
  }

  private Map<String, Object> obtenerEsquema(UUID version) {
    String textoEsquema =
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
      return json.readValue(textoEsquema, new TypeReference<>() {});
    } catch (JsonProcessingException error) {
      throw ExtractorElementosDeDano.invalido(
          "$", "El esquema persistido debe ser un objeto JSON.");
    }
  }

  private Map<String, CampoCritico> extraerCamposCriticos(Map<String, Object> esquema) {
    Map<String, CampoCritico> resultado = new HashMap<>();
    recorrer(esquema, List.of(), false, resultado);
    return Map.copyOf(resultado);
  }

  private void recorrer(
      Map<?, ?> nodo,
      List<String> rutaActual,
      boolean repetido,
      Map<String, CampoCritico> resultado) {
    if (!repetido
        && Boolean.TRUE.equals(nodo.get("x-dano"))
        && nodo.get("x-elemento-ref") instanceof String ref
        && !ref.isBlank()
        && nodo.get("x-anulacion-critica") instanceof String marcador
        && !marcador.isBlank()) {
      Anulacion anulacion = traducirMarcador(marcador);
      if (anulacion != null) {
        resultado.putIfAbsent(ref, new CampoCritico(List.copyOf(rutaActual), anulacion));
      }
    }
    if (nodo.get("properties") instanceof Map<?, ?> propiedades) {
      for (var entrada : propiedades.entrySet()) {
        if (entrada.getValue() instanceof Map<?, ?> hijo) {
          var siguiente = new ArrayList<>(rutaActual);
          siguiente.add(entrada.getKey().toString());
          recorrer(hijo, siguiente, repetido, resultado);
        }
      }
    }
  }

  static Anulacion traducirMarcador(String marcador) {
    String normalizado = marcador.trim().toLowerCase(Locale.ROOT);
    return switch (normalizado) {
      case "socavacion_cimentacion", "socavacion" -> Anulacion.SOCAVACION;
      case "asentamiento_estructura", "asentamiento" -> Anulacion.ASENTAMIENTO;
      case "elemento_portante_cortado", "perdida_seccion" -> Anulacion.PERDIDA_SECCION;
      case "ausencia_total_elemento", "ausencia_portante" -> Anulacion.AUSENCIA_PORTANTE;
      default -> null;
    };
  }

  private int leerCalificacion(List<String> ruta, Map<String, Object> datos) {
    Object cursor = datos;
    for (String paso : ruta) {
      if (!(cursor instanceof Map<?, ?> mapa) || !mapa.containsKey(paso)) {
        return 0;
      }
      cursor = mapa.get(paso);
    }
    if (cursor instanceof Number numero) {
      try {
        return new BigDecimal(numero.toString()).intValueExact();
      } catch (ArithmeticException error) {
        return 0;
      }
    }
    return 0;
  }

  private record CampoCritico(List<String> ruta, Anulacion anulacion) {}
}

