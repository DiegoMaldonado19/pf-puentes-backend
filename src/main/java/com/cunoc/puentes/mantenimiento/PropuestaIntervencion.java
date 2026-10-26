package com.cunoc.puentes.mantenimiento;

/**
 * Sugerencia generada por el catálogo de mapeo daño → intervención al evaluar una inspección
 * publicada (RN-MTO-02, RN-MTO-03, RN-MTO-06).
 */
public record PropuestaIntervencion(
    String elementoRef,
    TipoOrden tipo,
    Prioridad prioridadSugerida,
    String descripcion,
    int calificacionDano) {

  public PropuestaIntervencion {
    if (elementoRef == null || elementoRef.isBlank()) {
      throw new IllegalArgumentException("La referencia del elemento afectado es obligatoria.");
    }
    if (tipo == null || prioridadSugerida == null) {
      throw new IllegalArgumentException(
          "El tipo de intervención y la prioridad sugerida son obligatorios.");
    }
    if (descripcion == null || descripcion.isBlank()) {
      throw new IllegalArgumentException("La descripción de la intervención es obligatoria.");
    }
    if (calificacionDano < 0 || calificacionDano > 4) {
      throw new IllegalArgumentException("La calificación de daño debe estar entre 0 y 4.");
    }
    elementoRef = elementoRef.trim();
    descripcion = descripcion.trim();
  }
}
