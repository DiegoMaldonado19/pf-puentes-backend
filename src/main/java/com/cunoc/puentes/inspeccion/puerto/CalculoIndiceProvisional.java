package com.cunoc.puentes.inspeccion.puerto;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.indice.Anulacion;
import com.cunoc.puentes.indice.CalculadoraIndiceCondicion;
import com.cunoc.puentes.indice.CalificacionElemento;
import com.cunoc.puentes.indice.CondicionesCriticas;
import com.cunoc.puentes.indice.ResultadoIndiceCondicion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Adaptador provisional del contrato 3: pesos vigentes de {@code version_pesos} × rutas de daño ×
 * {@code CalculadoraIndiceCondicion}. Sin pesos o sin calificaciones, {@code 422 ic-no-calculable}.
 */
class CalculoIndiceProvisional implements CalculoIndice {

  private final JdbcClient jdbc;
  private final ConsultaFormulario formularios;
  private final CalculadoraIndiceCondicion calculadora = new CalculadoraIndiceCondicion();

  CalculoIndiceProvisional(JdbcClient jdbc, ConsultaFormulario formularios) {
    this.jdbc = jdbc;
    this.formularios = formularios;
  }

  @Override
  public ResultadoCalculo calcular(Map<String, Object> datos, UUID formularioVersionId) {
    if (datos == null || formularioVersionId == null) {
      throw new IllegalArgumentException("Los datos y la versión del formulario son obligatorios.");
    }
    Optional<UUID> versionPesos = vigente(formularioVersionId);
    if (versionPesos.isEmpty()) {
      throw noCalculable("No hay un juego de pesos vigente para esa versión del formulario.");
    }
    List<Peso> pesos = pesos(versionPesos.get());
    if (pesos.isEmpty()) {
      throw noCalculable("El juego de pesos vigente no tiene elementos.");
    }
    List<CalificacionElemento> elementos = new ArrayList<>();
    for (Peso peso : pesos) {
      Integer calificacion = calificacion(datos, peso.elementoRef());
      if (calificacion == null) {
        throw noCalculable(
            "Falta la calificación del elemento '%s' para calcular el IC."
                .formatted(peso.elementoRef()));
      }
      elementos.add(new CalificacionElemento(calificacion, peso.peso()));
    }
    ResultadoIndiceCondicion resultado =
        calculadora.calcular(elementos, condiciones(datos, formularioVersionId));
    return new ResultadoCalculo(
        resultado.indiceCondicion().setScale(2, RoundingMode.HALF_UP),
        resultado.estadoCalculado(),
        anulacion(resultado.anulaciones()),
        versionPesos.get());
  }

  private Optional<UUID> vigente(UUID formularioVersionId) {
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

  private List<Peso> pesos(UUID versionPesosId) {
    return jdbc.sql(
            "select elemento_ref, peso from peso_elemento where version_pesos_id = :id order by elemento_ref")
        .param("id", versionPesosId)
        .query((rs, n) -> new Peso(rs.getString("elemento_ref"), rs.getBigDecimal("peso")))
        .list();
  }

  private static Integer calificacion(Map<String, Object> datos, String referencia) {
    Object directo = RutasDatos.leer(datos, referencia);
    Integer valor = entero(directo);
    if (valor != null) {
      return valor;
    }
    return entero(RutasDatos.leer(datos, referencia + ".calificacion"));
  }

  private CondicionesCriticas condiciones(Map<String, Object> datos, UUID versionId) {
    boolean socavacion = false;
    boolean asentamiento = false;
    boolean perdida = false;
    boolean ausencia = false;
    for (String ruta : formularios.rutasDeDano(versionId)) {
      Object valor = RutasDatos.leer(datos, ruta);
      if (!positivo(valor)) {
        continue;
      }
      String cola = cola(ruta);
      socavacion |= cola.contains("socavacion");
      asentamiento |= cola.contains("asentamiento");
      perdida |= cola.contains("perdida_seccion") || cola.contains("portante_cortado");
      ausencia |= cola.contains("ausencia_portante");
    }
    socavacion |= positivo(RutasDatos.leer(datos, "condiciones.socavacion_cimentacion_expuesta"));
    asentamiento |= positivo(RutasDatos.leer(datos, "condiciones.asentamiento_estructura"));
    perdida |= positivo(RutasDatos.leer(datos, "condiciones.portante_perdida_seccion"));
    ausencia |= positivo(RutasDatos.leer(datos, "condiciones.ausencia_portante"));
    return new CondicionesCriticas(socavacion, asentamiento, perdida, ausencia);
  }

  private static Anulacion anulacion(Set<Anulacion> causas) {
    for (Anulacion causa : Anulacion.values()) {
      if (causas.contains(causa)) {
        return causa;
      }
    }
    return null;
  }

  private static Integer entero(Object valor) {
    if (!(valor instanceof Number numero)) {
      return null;
    }
    double crudo = numero.doubleValue();
    if (crudo != Math.rint(crudo)) {
      return null;
    }
    return numero.intValue();
  }

  private static boolean positivo(Object valor) {
    if (valor instanceof Boolean bandera) {
      return bandera;
    }
    Integer numero = entero(valor);
    return numero != null && numero > 0;
  }

  private static String cola(String ruta) {
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

  private record Peso(String elementoRef, BigDecimal peso) {

    Peso {
      Objects.requireNonNull(elementoRef);
      Objects.requireNonNull(peso);
    }
  }
}
