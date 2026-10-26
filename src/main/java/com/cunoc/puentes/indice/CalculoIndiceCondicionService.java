package com.cunoc.puentes.indice;

import com.cunoc.puentes.common.NegocioException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fachada autoritativa del contrato 3 (P8 → P5) para calcular el Índice de Condición oficial al
 * publicar una inspección o estimar en borrador.
 */
@Service
public class CalculoIndiceCondicionService {

  private final PreparacionCalificacionesService preparacion;
  private final VersionPesosRepository versiones;
  private final DetectorCondicionesCriticas detector;
  private final CalculadoraIndiceCondicion calculadora = new CalculadoraIndiceCondicion();

  public CalculoIndiceCondicionService(
      PreparacionCalificacionesService preparacion,
      VersionPesosRepository versiones,
      DetectorCondicionesCriticas detector) {
    this.preparacion = preparacion;
    this.versiones = versiones;
    this.detector = detector;
  }

  /**
   * Calcula el IC con el juego más reciente de la versión del formulario y detecta automáticamente
   * las anulaciones críticas declaradas en el esquema JSON de P4.
   */
  @Transactional(readOnly = true)
  public ResultadoCalculoOficialIc calcular(UUID formularioVersionId, Map<String, Object> datos) {
    return calcular(formularioVersionId, null, datos, null);
  }

  /**
   * Calcula el IC permitiendo indicar un juego de pesos explícito o condiciones críticas
   * adicionales.
   */
  @Transactional(readOnly = true)
  public ResultadoCalculoOficialIc calcular(
      UUID formularioVersionId,
      UUID versionPesosId,
      Map<String, Object> datos,
      CondicionesCriticas condicionesExplicitas) {
    if (formularioVersionId == null || datos == null) {
      throw new IllegalArgumentException(
          "La versión del formulario y las respuestas son obligatorias.");
    }
    UUID juegoId = resolverJuegoPesos(formularioVersionId, versionPesosId);
    List<CalificacionElemento> calificaciones =
        preparacion.preparar(formularioVersionId, juegoId, datos);
    CondicionesCriticas detectadas = detector.detectar(formularioVersionId, datos);
    CondicionesCriticas efectivas =
        DetectorCondicionesCriticas.combinar(detectadas, condicionesExplicitas);

    ResultadoIndiceCondicion resultado = calculadora.calcular(calificaciones, efectivas);
    BigDecimal icRedondeado = resultado.indiceCondicion().setScale(2, RoundingMode.HALF_UP);
    Anulacion principal = seleccionarAnulacionPrincipal(resultado.anulaciones());

    return new ResultadoCalculoOficialIc(
        juegoId, icRedondeado, resultado.estadoCalculado(), resultado.anulaciones(), principal);
  }

  private UUID resolverJuegoPesos(UUID formularioVersionId, UUID versionPesosId) {
    if (versionPesosId != null) {
      return versionPesosId;
    }
    var pagina =
        versiones.findByFormularioVersionId(
            formularioVersionId,
            PageRequest.of(0, 1, Sort.by(Sort.Order.desc("vigenteDesde"), Sort.Order.desc("id"))));
    if (pagina.isEmpty()) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "sin-juegos-de-pesos",
          "Sin juegos de pesos configurados",
          "La versión de formulario no cuenta con juegos de pesos configurados para calcular el IC.");
    }
    return pagina.getContent().getFirst().getId();
  }

  private Anulacion seleccionarAnulacionPrincipal(Set<Anulacion> anulaciones) {
    if (anulaciones == null || anulaciones.isEmpty()) {
      return null;
    }
    for (Anulacion candidata : Anulacion.values()) {
      if (anulaciones.contains(candidata)) {
        return candidata;
      }
    }
    return null;
  }
}
