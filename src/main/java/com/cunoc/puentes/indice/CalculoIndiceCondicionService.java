package com.cunoc.puentes.indice;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.inspeccion.puerto.CalculoIndice;
import com.cunoc.puentes.inspeccion.puerto.ConsultaFormulario;
import com.cunoc.puentes.inspeccion.puerto.RutasDatos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fachada autoritativa del contrato 3 (P8 → P5) para calcular el Índice de Condición oficial al
 * publicar una inspección o estimar en borrador. Implementa el puerto {@link CalculoIndice}
 * consumido por {@code PublicacionInspeccionService}.
 */
@Service
public class CalculoIndiceCondicionService implements CalculoIndice {

  private final PreparacionCalificacionesService preparacion;
  private final VersionPesosRepository versiones;
  private final DetectorCondicionesCriticas detector;
  private final MetadatosDanoFormulario metadatos;
  private final JdbcClient jdbc;
  private final ConsultaFormulario formularios;
  private final CalculadoraIndiceCondicion calculadora = new CalculadoraIndiceCondicion();

  public CalculoIndiceCondicionService(
      PreparacionCalificacionesService preparacion,
      VersionPesosRepository versiones,
      DetectorCondicionesCriticas detector,
      MetadatosDanoFormulario metadatos,
      JdbcClient jdbc,
      ConsultaFormulario formularios) {
    this.preparacion = preparacion;
    this.versiones = versiones;
    this.detector = detector;
    this.metadatos = metadatos;
    this.jdbc = jdbc;
    this.formularios = formularios;
  }

  /**
   * Implementación del puerto {@link CalculoIndice} (Contrato 3 hacia P5).
   *
   * <p>Cuando el esquema de la versión de formulario define metadatos de daño ({@code x-dano:
   * true}), utiliza {@link PreparacionCalificacionesService} y {@link DetectorCondicionesCriticas}.
   * Cuando el esquema no declara metadatos {@code x-dano} (como en los fixtures sintéticos de P5),
   * evalúa los pesos vigentes por rutas de datos manteniendo el contrato {@code ic-no-calculable}.
   */
  @Override
  @Transactional(readOnly = true)
  public CalculoIndice.ResultadoCalculo calcular(
      Map<String, Object> datos, UUID formularioVersionId) {
    if (datos == null || formularioVersionId == null) {
      throw new IllegalArgumentException("Los datos y la versión del formulario son obligatorios.");
    }
    Map<String, CampoDeDano> camposEsquema = obtenerCamposDanoSiExisten(formularioVersionId);
    if (!camposEsquema.isEmpty()) {
      try {
        ResultadoCalculoOficialIc oficial = calcular(formularioVersionId, null, datos, null);
        return new CalculoIndice.ResultadoCalculo(
            oficial.indiceCondicion(),
            oficial.estadoCalculado(),
            oficial.anulacionPrincipal(),
            oficial.versionPesosId());
      } catch (NegocioException error) {
        if (error.getStatusCode() == HttpStatus.UNPROCESSABLE_CONTENT) {
          throw new NegocioException(
              HttpStatus.UNPROCESSABLE_CONTENT,
              "ic-no-calculable",
              "No se puede calcular el índice de condición",
              error.getMessage());
        }
        throw error;
      }
    }
    return calcularPorRutasDirectas(datos, formularioVersionId);
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

  private Map<String, CampoDeDano> obtenerCamposDanoSiExisten(UUID formularioVersionId) {
    try {
      return metadatos.campos(formularioVersionId);
    } catch (NegocioException error) {
      return Map.of();
    }
  }

  private CalculoIndice.ResultadoCalculo calcularPorRutasDirectas(
      Map<String, Object> datos, UUID formularioVersionId) {
    Optional<UUID> versionPesos = vigenteSql(formularioVersionId);
    if (versionPesos.isEmpty()) {
      throw noCalculable("No hay un juego de pesos vigente para esa versión del formulario.");
    }
    List<PesoFila> pesos = pesosSql(versionPesos.get());
    if (pesos.isEmpty()) {
      throw noCalculable("El juego de pesos vigente no tiene elementos.");
    }
    List<CalificacionElemento> elementos = new ArrayList<>();
    for (PesoFila peso : pesos) {
      Integer calificacion = calificacionDirecta(datos, peso.elementoRef());
      if (calificacion == null) {
        throw noCalculable(
            "Falta la calificación del elemento '%s' para calcular el IC."
                .formatted(peso.elementoRef()));
      }
      elementos.add(new CalificacionElemento(calificacion, peso.peso()));
    }
    ResultadoIndiceCondicion resultado =
        calculadora.calcular(elementos, condicionesPorRutas(datos, formularioVersionId));
    return new CalculoIndice.ResultadoCalculo(
        resultado.indiceCondicion().setScale(2, RoundingMode.HALF_UP),
        resultado.estadoCalculado(),
        seleccionarAnulacionPrincipal(resultado.anulaciones()),
        versionPesos.get());
  }

  private Optional<UUID> vigenteSql(UUID formularioVersionId) {
    return jdbc.sql(
            """
            select id from version_pesos
            where formulario_version_id = :formulario
            order by vigente_desde desc, id desc
            limit 1
            """)
        .param("formulario", formularioVersionId)
        .query(UUID.class)
        .optional();
  }

  private List<PesoFila> pesosSql(UUID versionPesosId) {
    return jdbc.sql(
            "select elemento_ref, peso from peso_elemento where version_pesos_id = :id order by elemento_ref")
        .param("id", versionPesosId)
        .query((rs, n) -> new PesoFila(rs.getString("elemento_ref"), rs.getBigDecimal("peso")))
        .list();
  }

  private static Integer calificacionDirecta(Map<String, Object> datos, String referencia) {
    Object directo = RutasDatos.leer(datos, referencia);
    Integer valor = enteroDirecto(directo);
    if (valor != null) {
      return valor;
    }
    return enteroDirecto(RutasDatos.leer(datos, referencia + ".calificacion"));
  }

  private CondicionesCriticas condicionesPorRutas(Map<String, Object> datos, UUID versionId) {
    boolean socavacion = false;
    boolean asentamiento = false;
    boolean perdida = false;
    boolean ausencia = false;
    for (String ruta : formularios.rutasDeDano(versionId)) {
      Object valor = RutasDatos.leer(datos, ruta);
      if (!positivoDirecto(valor)) {
        continue;
      }
      String cola = colaRuta(ruta);
      socavacion |= cola.contains("socavacion");
      asentamiento |= cola.contains("asentamiento");
      perdida |= cola.contains("perdida_seccion") || cola.contains("portante_cortado");
      ausencia |= cola.contains("ausencia_portante");
    }
    socavacion |=
        positivoDirecto(RutasDatos.leer(datos, "condiciones.socavacion_cimentacion_expuesta"));
    asentamiento |= positivoDirecto(RutasDatos.leer(datos, "condiciones.asentamiento_estructura"));
    perdida |= positivoDirecto(RutasDatos.leer(datos, "condiciones.portante_perdida_seccion"));
    ausencia |= positivoDirecto(RutasDatos.leer(datos, "condiciones.ausencia_portante"));
    return new CondicionesCriticas(socavacion, asentamiento, perdida, ausencia);
  }

  private static Integer enteroDirecto(Object valor) {
    if (!(valor instanceof Number numero)) {
      return null;
    }
    double crudo = numero.doubleValue();
    if (crudo != Math.rint(crudo)) {
      return null;
    }
    return numero.intValue();
  }

  private static boolean positivoDirecto(Object valor) {
    if (valor instanceof Boolean bandera) {
      return bandera;
    }
    Integer numero = enteroDirecto(valor);
    return numero != null && numero > 0;
  }

  private static String colaRuta(String ruta) {
    int punto = ruta.lastIndexOf('.');
    return punto < 0 ? ruta : ruta.substring(punto + 1);
  }

  private static NegocioException noCalculable(String detalle) {
    return new NegocioException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "ic-no-calculable",
        "No se puede calcular el índice de condición",
        detalle);
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

  private record PesoFila(String elementoRef, BigDecimal peso) {}
}
