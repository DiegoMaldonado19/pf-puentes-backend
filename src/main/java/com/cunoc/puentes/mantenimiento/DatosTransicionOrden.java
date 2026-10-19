package com.cunoc.puentes.mantenimiento;

import java.time.LocalDate;

/**
 * Entrada interna del dominio. El futuro servicio obtiene las fotos guardadas de ArchivoService,
 * nunca de un contador enviado por el cliente HTTP.
 */
record DatosTransicionOrden(
    LocalDate fechaEjecucion, String responsable, long fotosGuardadas, String motivo) {

  DatosTransicionOrden {
    if (fotosGuardadas < 0) {
      throw new IllegalArgumentException(
          "El número de fotografías guardadas no puede ser negativo.");
    }
  }
}
