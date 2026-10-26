package com.cunoc.puentes.indice;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.indice.dto.ConfirmacionEstadoDTO;
import com.cunoc.puentes.indice.dto.EstimacionPreliminarIcDTO;
import com.cunoc.puentes.indice.dto.ResultadoConfirmacionEstadoDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.RoundingMode;
import java.util.List;
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
 * Servicio para estimación preliminar del IC en borradores (RN-IC-07) y validación de confirmación
 * de estado por el evaluador (RN-IC-06).
 *
 * <p>No altera la inspección, el inventario de puentes ni las condiciones históricas.
 */
@Service
@PreAuthorize("hasAnyRole('ESTUDIANTE', 'CATEDRATICO', 'ADMINISTRADOR', 'PROFESIONAL_EXTERNO')")
public class EstimacionPreliminarIcService {

  private static final String SQL_INSPECCION =
      """
      SELECT id, puente_id, autor_id, formulario_version_id, estado,
             datos::text AS datos_json, anulacion
      FROM inspeccion
      WHERE id = ?
      """;

  private final PreparacionCalificacionesService preparacion;
  private final VersionPesosRepository versiones;
  private final DetectorCondicionesCriticas detector;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json = new ObjectMapper();
  private final CalculadoraIndiceCondicion calculadora = new CalculadoraIndiceCondicion();

  public EstimacionPreliminarIcService(
      PreparacionCalificacionesService preparacion,
      VersionPesosRepository versiones,
      DetectorCondicionesCriticas detector,
      JdbcTemplate jdbc) {
    this.preparacion = preparacion;
    this.versiones = versiones;
    this.detector = detector;
    this.jdbc = jdbc;
  }

  /**
   * Estima el IC preliminar a partir de las respuestas recolectadas del formulario.
   *
   * <p>Si no se especifica juego de pesos, utiliza el juego más reciente configurado para la
   * versión.
   */
  @Transactional(readOnly = true)
  public EstimacionPreliminarIcDTO estimar(
      UUID formularioVersionId,
      UUID versionPesosId,
      Map<String, Object> datos,
      CondicionesCriticas condiciones) {
    if (formularioVersionId == null) {
      throw new IllegalArgumentException("La versión del formulario es obligatoria.");
    }
    if (datos == null) {
      throw new IllegalArgumentException("Las respuestas del formulario son obligatorias.");
    }

    UUID juegoId = versionPesosId;
    if (juegoId == null) {
      var pagina =
          versiones.findByFormularioVersionId(
              formularioVersionId,
              PageRequest.of(
                  0, 1, Sort.by(Sort.Order.desc("vigenteDesde"), Sort.Order.desc("id"))));
      if (pagina.isEmpty()) {
        throw new NegocioException(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "sin-juegos-de-pesos",
            "Sin juegos de pesos configurados",
            "La versión de formulario no cuenta con juegos de pesos configurados para estimar el IC.");
      }
      juegoId = pagina.getContent().getFirst().getId();
    }

    List<CalificacionElemento> calificaciones =
        preparacion.preparar(formularioVersionId, juegoId, datos);
    CondicionesCriticas detectadas = detector.detectar(formularioVersionId, datos);
    CondicionesCriticas criticas = DetectorCondicionesCriticas.combinar(detectadas, condiciones);
    ResultadoIndiceCondicion resultado = calculadora.calcular(calificaciones, criticas);

    return EstimacionPreliminarIcDTO.de(
        resultado.indiceCondicion().setScale(2, RoundingMode.HALF_UP),
        resultado.estadoCalculado(),
        resultado.anulaciones(),
        juegoId);
  }

  /**
   * Estima el IC preliminar para una inspección no publicada sin modificar sus datos ni el puente.
   */
  @Transactional(readOnly = true)
  public EstimacionPreliminarIcDTO estimarParaInspeccion(UUID inspeccionId, UUID versionPesosId) {
    if (inspeccionId == null) {
      throw new IllegalArgumentException("El identificador de la inspección es obligatorio.");
    }

    List<InspeccionParaEstimacion> filas =
        jdbc.query(
            SQL_INSPECCION,
            (rs, rowNum) ->
                new InspeccionParaEstimacion(
                    rs.getObject("id", UUID.class),
                    rs.getObject("formulario_version_id", UUID.class),
                    rs.getString("estado"),
                    rs.getString("datos_json"),
                    rs.getString("anulacion")),
            inspeccionId);

    if (filas.isEmpty()) {
      throw new NegocioException(
          HttpStatus.NOT_FOUND,
          "inspeccion-no-encontrada",
          "Inspección no encontrada",
          "La inspección indicada no existe.");
    }

    InspeccionParaEstimacion fila = filas.getFirst();
    if ("PUBLICADA".equals(fila.estado())) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "inspeccion-ya-publicada",
          "Inspección ya publicada",
          "La inspección ya está publicada y posee un IC definitivo oficial. Usa la consulta de recálculos.");
    }

    Map<String, Object> datos;
    try {
      datos = json.readValue(fila.datosJson(), new TypeReference<Map<String, Object>>() {});
    } catch (JsonProcessingException e) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "datos-inspeccion-invalidos",
          "Datos de inspección inválidos",
          "Las respuestas guardadas en la inspección no son un JSON válido.");
    }

    CondicionesCriticas criticas = resolverCondiciones(fila.anulacion());
    return estimar(fila.formularioVersionId(), versionPesosId, datos, criticas);
  }

  /** Valida la confirmación del estado de condición y justificación según RN-IC-06. */
  public ResultadoConfirmacionEstadoDTO validarConfirmacion(ConfirmacionEstadoDTO solicitud) {
    if (solicitud == null) {
      throw new IllegalArgumentException("La solicitud de confirmación es obligatoria.");
    }

    try {
      ConfirmacionEstadoCondicion dominio =
          ConfirmacionEstadoCondicion.de(
              solicitud.estadoCalculado(),
              solicitud.estadoConfirmado(),
              solicitud.justificacionEstado(),
              solicitud.anulacion());

      return new ResultadoConfirmacionEstadoDTO(
          dominio.estadoCalculado(),
          dominio.estadoConfirmado(),
          dominio.justificacionEstado(),
          dominio.anulacion(),
          dominio.tieneDiscrepancia(),
          dominio.esElevacionDeAnulacionCritica(),
          dominio.estadoEfectivo());
    } catch (IllegalArgumentException e) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "confirmacion-estado-invalida",
          "Confirmación de estado inválida",
          e.getMessage());
    }
  }

  private CondicionesCriticas resolverCondiciones(String anulacionStr) {
    if (anulacionStr == null || anulacionStr.isBlank()) {
      return new CondicionesCriticas(false, false, false, false);
    }
    return new CondicionesCriticas(
        "SOCAVACION".equalsIgnoreCase(anulacionStr),
        "ASENTAMIENTO".equalsIgnoreCase(anulacionStr),
        "PERDIDA_SECCION".equalsIgnoreCase(anulacionStr),
        "AUSENCIA_PORTANTE".equalsIgnoreCase(anulacionStr));
  }

  private record InspeccionParaEstimacion(
      UUID id, UUID formularioVersionId, String estado, String datosJson, String anulacion) {}
}
