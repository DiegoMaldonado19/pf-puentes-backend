package com.cunoc.puentes.indice;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.indice.dto.ComparacionRecalculoIcDTO;
import com.cunoc.puentes.indice.dto.PaginaPesosDTO;
import com.cunoc.puentes.indice.dto.RecalculoIcDTO;
import com.cunoc.puentes.indice.dto.ResultadoOriginalIcDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra y consulta recálculos fechados del IC sin alterar la inspección publicada ni los campos
 * derivados del puente (RN-IC-08, RN-INS-07).
 */
@Service
@PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
public class RecalculoIcService {

  private static final String SQL_INSPECCION =
      """
      SELECT i.id AS inspeccion_id,
             i.puente_id,
             i.formulario_version_id,
             i.estado,
             i.datos::text AS datos_json,
             i.version_pesos_id,
             vp.acta,
             vp.vigente_desde,
             i.indice_condicion,
             i.estado_calculado,
             i.anulacion,
             i.estado_confirmado,
             i.justificacion_estado,
             i.publicada_en
      FROM inspeccion i
      LEFT JOIN version_pesos vp ON vp.id = i.version_pesos_id
      WHERE i.id = ?
      """;

  private final JdbcTemplate jdbc;
  private final RecalculoIcRepository recalculos;
  private final VersionPesosRepository versiones;
  private final PreparacionCalificacionesService preparacion;
  private final CreacionRecalculoIcService creador;
  private final PesosMapper mapper;
  private final ObjectMapper json = new ObjectMapper();
  private final CalculadoraIndiceCondicion calculadora = new CalculadoraIndiceCondicion();

  RecalculoIcService(
      JdbcTemplate jdbc,
      RecalculoIcRepository recalculos,
      VersionPesosRepository versiones,
      PreparacionCalificacionesService preparacion,
      CreacionRecalculoIcService creador,
      PesosMapper mapper) {
    this.jdbc = jdbc;
    this.recalculos = recalculos;
    this.versiones = versiones;
    this.preparacion = preparacion;
    this.creador = creador;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public ComparacionRecalculoIcDTO consultar(UUID inspeccionId, int pagina, int tamano) {
    if (inspeccionId == null) {
      throw new IllegalArgumentException("La inspección es obligatoria.");
    }
    if (pagina < 0 || tamano < 1 || tamano > 100 || (long) pagina * tamano > Integer.MAX_VALUE) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "pagina-de-recalculos-invalida",
          "Paginación inválida",
          "Usa una página no negativa y un tamaño entre 1 y 100 dentro del límite admitido.");
    }
    InspeccionParaRecalculo fila = obtenerOriginal(inspeccionId, false);
    var paginaRecalculos =
        recalculos
            .findByInspeccionId(
                inspeccionId, PageRequest.of(pagina, tamano, Sort.by("id").descending()))
            .map(mapper::aDTO);
    return new ComparacionRecalculoIcDTO(fila.original(), PaginaPesosDTO.de(paginaRecalculos));
  }

  @Transactional
  public RecalculoIcDTO recalcular(UUID inspeccionId, UUID versionPesosId) {
    if (inspeccionId == null || versionPesosId == null) {
      throw new IllegalArgumentException("La inspección y el juego de pesos son obligatorios.");
    }
    InspeccionParaRecalculo fila = obtenerOriginal(inspeccionId, true);
    var existente = recalculos.findByInspeccionIdAndVersionPesosId(inspeccionId, versionPesosId);
    if (existente.isPresent()) {
      return mapper.aDTO(existente.get());
    }
    Map<String, Object> datos = leerDatos(fila.datosJson());
    var calificaciones =
        preparacion.preparar(fila.original().formularioVersionId(), versionPesosId, datos);
    VersionPesos version = versiones.findById(versionPesosId).orElseThrow();
    Anulacion anulacion = fila.original().anulacion();
    ResultadoIndiceCondicion resultado =
        calculadora.calcular(calificaciones, condicionesDe(anulacion));
    BigDecimal indicePersistido = resultado.indiceCondicion().setScale(2, RoundingMode.HALF_UP);
    RecalculoIc guardado =
        creador.crear(
            inspeccionId, version, indicePersistido, resultado.estadoCalculado(), anulacion);
    return mapper.aDTO(guardado);
  }

  private InspeccionParaRecalculo obtenerOriginal(UUID inspeccionId, boolean bloquear) {
    String consulta = bloquear ? SQL_INSPECCION + " FOR UPDATE OF i" : SQL_INSPECCION;
    InspeccionParaRecalculo fila =
        jdbc
            .query(
                consulta,
                (rs, numero) -> {
                  if (!"PUBLICADA".equals(rs.getString("estado"))) {
                    return new InspeccionParaRecalculo(false, null, null);
                  }
                  String anulacionTexto = rs.getString("anulacion");
                  Anulacion anulacion =
                      anulacionTexto == null ? null : Anulacion.valueOf(anulacionTexto);
                  ResultadoOriginalIcDTO original =
                      new ResultadoOriginalIcDTO(
                          rs.getObject("inspeccion_id", UUID.class),
                          rs.getObject("puente_id", UUID.class),
                          rs.getObject("formulario_version_id", UUID.class),
                          rs.getObject("version_pesos_id", UUID.class),
                          rs.getString("acta"),
                          rs.getTimestamp("vigente_desde").toInstant(),
                          rs.getBigDecimal("indice_condicion"),
                          EstadoCondicion.valueOf(rs.getString("estado_calculado")),
                          anulacion,
                          EstadoCondicion.valueOf(rs.getString("estado_confirmado")),
                          rs.getString("justificacion_estado"),
                          rs.getTimestamp("publicada_en").toInstant());
                  return new InspeccionParaRecalculo(true, original, rs.getString("datos_json"));
                },
                inspeccionId)
            .stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "inspeccion-no-encontrada",
                        "Inspección no encontrada",
                        "La inspección solicitada no existe."));
    if (!fila.publicada()) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "inspeccion-no-publicada",
          "Inspección no publicada",
          "Solo las inspecciones publicadas tienen un resultado oficial comparable con recálculos.");
    }
    return fila;
  }

  private Map<String, Object> leerDatos(String datosJson) {
    try {
      return json.readValue(datosJson, new TypeReference<>() {});
    } catch (JsonProcessingException error) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "datos-inspeccion-invalidos",
          "Respuestas de inspección inválidas",
          "Las respuestas persistidas de la inspección deben conformar un objeto JSON.");
    }
  }

  private static CondicionesCriticas condicionesDe(Anulacion anulacion) {
    return new CondicionesCriticas(
        anulacion == Anulacion.SOCAVACION,
        anulacion == Anulacion.ASENTAMIENTO,
        anulacion == Anulacion.PERDIDA_SECCION,
        anulacion == Anulacion.AUSENCIA_PORTANTE);
  }

  private record InspeccionParaRecalculo(
      boolean publicada, ResultadoOriginalIcDTO original, String datosJson) {}
}

