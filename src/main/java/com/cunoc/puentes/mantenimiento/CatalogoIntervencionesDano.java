package com.cunoc.puentes.mantenimiento;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Evaluador puro del catálogo daño → intervención y cálculo de prioridad sugerida (RN-MTO-02,
 * RN-MTO-03, RN-MTO-06).
 *
 * <p>Reglas configuradas:
 *
 * <ul>
 *   <li>Calificación 0 o 1 (sin daño o incipiente): no genera orden por daño puntual.
 *   <li>Calificación 2 (moderado): {@link TipoOrden#PREVENTIVO} (salvo anulación crítica activa).
 *   <li>Calificación 3 (severo): {@link TipoOrden#EMERGENCIA} si activa anulación crítica; de lo
 *       contrario {@link TipoOrden#CORRECTIVO}.
 *   <li>Calificación 4 (falla estructural): {@link TipoOrden#EMERGENCIA}.
 * </ul>
 */
public final class CatalogoIntervencionesDano {

  public static final int UMBRAL_MINIMO_PROPUESTA = 2;
  public static final int CALIFICACION_PREVENTIVA = 2;
  public static final int CALIFICACION_CORRECTIVA = 3;
  public static final int CALIFICACION_EMERGENCIA = 4;

  public static final BigDecimal UMBRAL_IC_MALO = new BigDecimal("50");
  public static final BigDecimal UMBRAL_IC_BUENO = new BigDecimal("75");
  public static final BigDecimal PESO_ELEMENTO_ALTO = new BigDecimal("2.0");

  private static final Map<String, String> NOMBRES_ELEMENTOS =
      Map.ofEntries(
          Map.entry(
              "subestructura.estribo_entrada.grietas",
              "cortina y aleros del estribo de entrada (fisuras/grietas)"),
          Map.entry(
              "subestructura.estribo_entrada.socavacion",
              "cimentación del estribo de entrada (socavación)"),
          Map.entry(
              "subestructura.estribo_salida.grietas",
              "cortina y aleros del estribo de salida (fisuras/grietas)"),
          Map.entry(
              "subestructura.estribo_salida.socavacion",
              "cimentación del estribo de salida (socavación)"),
          Map.entry(
              "subestructura.pilas.grietas", "fuste y cabezal de pilas intermedias (grietas)"),
          Map.entry("subestructura.pilas.socavacion", "apoyos de pilas intermedias (socavación)"),
          Map.entry(
              "subestructura.cimentacion.asentamiento",
              "cimentación y apoyos estructurales (asentamiento)"),
          Map.entry(
              "superestructura.vigas.deformacion", "vigas principales (deflexión o deformación)"),
          Map.entry(
              "superestructura.vigas.perdida_seccion",
              "vigas principales (pérdida de sección o corte en elemento portante)"),
          Map.entry(
              "superestructura.vigas.ausencia_elemento",
              "vigas principales (ausencia de elemento portante)"),
          Map.entry("superestructura.losa.agrietamiento", "losa de rodadura (agrietamiento)"),
          Map.entry("superestructura.juntas.deterioro", "juntas de dilatación (deterioro)"),
          Map.entry("superestructura.apoyos.deterioro", "aparatos de apoyo (deterioro)"),
          Map.entry("elementos_no_estructurales.barandas.danos", "barandas de protección vial"),
          Map.entry("elementos_no_estructurales.aceras.danos", "aceras peatonales"),
          Map.entry("elementos_no_estructurales.drenajes.danos", "sistema de drenajes y bajantes"),
          Map.entry("caracteristicas_cauce.obstruccion", "cauce bajo el puente (obstrucción)"),
          Map.entry("caracteristicas_cauce.alineamiento", "encauzamiento y alineamiento del flujo"),
          Map.entry(
              "caracteristicas_cauce.proteccion_margenes",
              "muros de protección de márgenes y gaviones"));

  /**
   * Evalúa un elemento calificado y devuelve la propuesta de intervención si alcanza el umbral
   * mínimo de atención.
   */
  public Optional<PropuestaIntervencion> evaluar(
      String elementoRef,
      int calificacion,
      BigDecimal pesoElemento,
      BigDecimal indiceCondicion,
      boolean esAnulacionCritica) {
    if (elementoRef == null || elementoRef.isBlank()) {
      throw new IllegalArgumentException("La referencia del elemento es obligatoria.");
    }
    if (calificacion < 0 || calificacion > 4) {
      throw new IllegalArgumentException("La calificación de daño debe estar entre 0 y 4.");
    }
    if (pesoElemento == null || pesoElemento.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("El peso del elemento debe ser positivo.");
    }
    if (indiceCondicion == null
        || indiceCondicion.compareTo(BigDecimal.ZERO) < 0
        || indiceCondicion.compareTo(new BigDecimal("100")) > 0) {
      throw new IllegalArgumentException("El índice de condición debe estar entre 0 y 100.");
    }

    if (calificacion < UMBRAL_MINIMO_PROPUESTA) {
      return Optional.empty();
    }

    TipoOrden tipo = determinarTipo(calificacion, esAnulacionCritica);
    Prioridad prioridad =
        calcularPrioridadSugerida(tipo, calificacion, pesoElemento, indiceCondicion);
    String descripcion = construirDescripcion(elementoRef.trim(), tipo, calificacion);

    return Optional.of(
        new PropuestaIntervencion(elementoRef.trim(), tipo, prioridad, descripcion, calificacion));
  }

  /** Determina la tipología de mantenimiento (RN-MTO-03) según severidad y criticidad. */
  public TipoOrden determinarTipo(int calificacion, boolean esAnulacionCritica) {
    if (calificacion >= CALIFICACION_EMERGENCIA || esAnulacionCritica) {
      return TipoOrden.EMERGENCIA;
    }
    if (calificacion == CALIFICACION_CORRECTIVA) {
      return TipoOrden.CORRECTIVO;
    }
    return TipoOrden.PREVENTIVO;
  }

  /**
   * Calcula la prioridad sugerida en función del IC del puente, la severidad y el peso del elemento
   * afectado (RN-MTO-06).
   */
  public Prioridad calcularPrioridadSugerida(
      TipoOrden tipo, int calificacion, BigDecimal pesoElemento, BigDecimal indiceCondicion) {
    boolean icMalo = indiceCondicion.compareTo(UMBRAL_IC_MALO) < 0;
    boolean icRegular = !icMalo && indiceCondicion.compareTo(UMBRAL_IC_BUENO) < 0;
    boolean pesoAlto = pesoElemento.compareTo(PESO_ELEMENTO_ALTO) >= 0;

    if (tipo == TipoOrden.EMERGENCIA
        || calificacion >= CALIFICACION_EMERGENCIA
        || icMalo
        || (calificacion == CALIFICACION_CORRECTIVA && pesoAlto)) {
      return Prioridad.ALTA;
    }
    if (calificacion == CALIFICACION_CORRECTIVA || icRegular || pesoAlto) {
      return Prioridad.MEDIA;
    }
    return Prioridad.BAJA;
  }

  private String construirDescripcion(String elementoRef, TipoOrden tipo, int calificacion) {
    String etiqueta = NOMBRES_ELEMENTOS.getOrDefault(elementoRef, elementoRef);
    String prefijo =
        switch (tipo) {
          case EMERGENCIA -> "Intervención de emergencia en ";
          case CORRECTIVO -> "Reparación correctiva en ";
          case PREVENTIVO -> "Mantenimiento preventivo en ";
          case RUTINARIO -> "Mantenimiento rutinario en ";
        };
    return prefijo + etiqueta + " (daño registrado grado " + calificacion + ").";
  }
}

