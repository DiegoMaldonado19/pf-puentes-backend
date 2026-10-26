package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.indice.CampoDeDano;
import com.cunoc.puentes.indice.DetectorCondicionesCriticas;
import com.cunoc.puentes.indice.MetadatosDanoFormulario;
import com.cunoc.puentes.inspeccion.InspeccionPublicada;
import com.cunoc.puentes.mantenimiento.dto.OrdenMantenimientoDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Genera propuestas de mantenimiento a partir de los daños de una inspección publicada
 * (HU-P8-MTO-02, RN-MTO-02, RN-MTO-03, RN-MTO-06) y asegura propuestas rutinarias anuales sin
 * duplicar (RN-MTO-07).
 */
@Service
public class GeneracionPropuestasMantenimientoService {

  private static final String DESCRIPCION_RUTINARIA_ANUAL =
      "Mantenimiento rutinario anual del puente: limpieza general de calzada, drenajes, juntas y cauce (RN-MTO-07).";

  private static final String SQL_INSPECCION =
      """
      SELECT id, puente_id, formulario_version_id, version_pesos_id, estado,
             datos::text AS datos_json
      FROM inspeccion
      WHERE id = ?
      """;

  private static final String SQL_PESOS =
      """
      SELECT elemento_ref, peso
      FROM peso_elemento
      WHERE version_pesos_id = ?
      """;

  private final OrdenMantenimientoRepository ordenes;
  private final CreacionPropuestaOrdenService creador;
  private final OrdenMantenimientoMapper mapper;
  private final MetadatosDanoFormulario metadatos;
  private final DetectorCondicionesCriticas detectorCriticas;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json = new ObjectMapper();
  private final CatalogoIntervencionesDano catalogo = new CatalogoIntervencionesDano();

  GeneracionPropuestasMantenimientoService(
      OrdenMantenimientoRepository ordenes,
      CreacionPropuestaOrdenService creador,
      OrdenMantenimientoMapper mapper,
      MetadatosDanoFormulario metadatos,
      DetectorCondicionesCriticas detectorCriticas,
      JdbcTemplate jdbc) {
    this.ordenes = ordenes;
    this.creador = creador;
    this.mapper = mapper;
    this.metadatos = metadatos;
    this.detectorCriticas = detectorCriticas;
    this.jdbc = jdbc;
  }

  /**
   * Evalúa los daños de la inspección publicada contra el catálogo daño → intervención y persiste
   * las propuestas resultantes en la misma transacción de publicación (Contrato 4).
   *
   * <p>Es idempotente por par {@code (inspeccion_id, elemento_ref)}.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<OrdenMantenimientoDTO> generarDesdeInspeccionPublicada(InspeccionPublicada evento) {
    validarEvento(evento);
    if (!ordenes.existePuente(evento.puenteId())) {
      throw new IllegalStateException(
          "InspeccionPublicada %s apunta al puente inexistente %s"
              .formatted(evento.inspeccionId(), evento.puenteId()));
    }

    List<FilaInspeccion> filas =
        jdbc.query(
            SQL_INSPECCION,
            (rs, rowNum) ->
                new FilaInspeccion(
                    rs.getObject("id", UUID.class),
                    rs.getObject("puente_id", UUID.class),
                    rs.getObject("formulario_version_id", UUID.class),
                    rs.getObject("version_pesos_id", UUID.class),
                    rs.getString("estado"),
                    rs.getString("datos_json")),
            evento.inspeccionId());

    if (filas.isEmpty()) {
      return List.of();
    }

    FilaInspeccion fila = filas.getFirst();
    if (!evento.puenteId().equals(fila.puenteId())) {
      throw new IllegalStateException(
          "La inspección %s no corresponde al puente %s del evento"
              .formatted(evento.inspeccionId(), evento.puenteId()));
    }
    if (!"PUBLICADA".equals(fila.estado())) {
      throw new IllegalStateException(
          "Los borradores o inspecciones no publicadas no generan propuestas de mantenimiento (%s está en %s)"
              .formatted(evento.inspeccionId(), fila.estado()));
    }

    Map<String, Object> datos = parsearDatos(fila.datosJson());
    Map<String, CampoDeDano> campos = new TreeMap<>(metadatos.campos(fila.formularioVersionId()));
    Set<String> referenciasCriticas =
        detectorCriticas.referenciasCriticasActivas(fila.formularioVersionId(), datos);
    Map<String, BigDecimal> pesosPorRef = cargarPesos(fila.versionPesosId());

    List<OrdenMantenimientoDTO> creadas = new ArrayList<>();
    for (var entrada : campos.entrySet()) {
      String elementoRef = entrada.getKey();
      CampoDeDano campo = entrada.getValue();
      if (campo.repetido()) {
        continue;
      }
      Optional<Integer> calificacionOpt = extraerCalificacionEntera(campo.ruta(), datos);
      if (calificacionOpt.isEmpty()) {
        continue;
      }
      int calificacion = calificacionOpt.get();
      BigDecimal peso = pesosPorRef.getOrDefault(elementoRef, BigDecimal.ONE);
      boolean esCritica = referenciasCriticas.contains(elementoRef);

      Optional<PropuestaIntervencion> propuestaOpt =
          catalogo.evaluar(elementoRef, calificacion, peso, evento.indiceCondicion(), esCritica);
      if (propuestaOpt.isPresent()
          && !ordenes.existePorInspeccionYElemento(evento.inspeccionId(), elementoRef)) {
        OrdenMantenimiento guardada =
            creador.crearPorDano(evento.puenteId(), evento.inspeccionId(), propuestaOpt.get());
        creadas.add(mapper.aDTO(guardada));
      }
    }
    return List.copyOf(creadas);
  }

  /**
   * Asegura de forma idempotente que exista una propuesta u orden activa de tipo {@link
   * TipoOrden#RUTINARIO} para el puente indicado (RN-MTO-07).
   */
  @Transactional
  public Optional<OrdenMantenimientoDTO> asegurarPropuestaRutinariaAnual(UUID puenteId) {
    if (puenteId == null) {
      throw new IllegalArgumentException("El identificador del puente es obligatorio.");
    }
    if (!ordenes.existePuente(puenteId)) {
      throw new NegocioException(
          HttpStatus.NOT_FOUND,
          "puente-no-encontrado",
          "Puente no encontrado",
          "El puente indicado no existe.");
    }
    if (ordenes.existeRutinariaAbierta(puenteId)) {
      return Optional.empty();
    }
    OrdenMantenimiento creada = creador.crearRutinariaAnual(puenteId, DESCRIPCION_RUTINARIA_ANUAL);
    return Optional.of(mapper.aDTO(creada));
  }

  private void validarEvento(InspeccionPublicada evento) {
    if (evento == null
        || evento.inspeccionId() == null
        || evento.puenteId() == null
        || evento.indiceCondicion() == null
        || evento.estado() == null
        || evento.fecha() == null) {
      throw new IllegalArgumentException("El evento InspeccionPublicada llegó incompleto.");
    }
  }

  private Map<String, Object> parsearDatos(String datosJson) {
    if (datosJson == null || datosJson.isBlank()) {
      return Map.of();
    }
    try {
      return json.readValue(datosJson, new TypeReference<Map<String, Object>>() {});
    } catch (JsonProcessingException error) {
      throw new IllegalStateException(
          "Los datos de la inspección publicada no son un objeto JSON válido.", error);
    }
  }

  private Map<String, BigDecimal> cargarPesos(UUID versionPesosId) {
    if (versionPesosId == null) {
      return Map.of();
    }
    Map<String, BigDecimal> resultado = new HashMap<>();
    jdbc.query(
        SQL_PESOS,
        rs -> {
          resultado.put(rs.getString("elemento_ref"), rs.getBigDecimal("peso"));
        },
        versionPesosId);
    return Map.copyOf(resultado);
  }

  private Optional<Integer> extraerCalificacionEntera(
      List<String> ruta, Map<String, Object> datos) {
    Object cursor = datos;
    for (String propiedad : ruta) {
      if (!(cursor instanceof Map<?, ?> objeto) || !objeto.containsKey(propiedad)) {
        return Optional.empty();
      }
      cursor = objeto.get(propiedad);
    }
    if (cursor instanceof Number numero) {
      try {
        int valor = new BigDecimal(numero.toString()).intValueExact();
        if (valor >= 0 && valor <= 4) {
          return Optional.of(valor);
        }
      } catch (ArithmeticException error) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private record FilaInspeccion(
      UUID id,
      UUID puenteId,
      UUID formularioVersionId,
      UUID versionPesosId,
      String estado,
      String datosJson) {}
}
