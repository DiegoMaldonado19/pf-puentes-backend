package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.common.ErrorCampo;
import com.cunoc.puentes.common.NegocioException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * RN-INS-03: la fecha se evalúa en America/Guatemala; no es futura ni anterior al año del puente.
 */
@Component
class ValidacionFechaInspeccion {

  static final ZoneId ZONA = ZoneId.of("America/Guatemala");

  private final Clock reloj;

  @Autowired
  ValidacionFechaInspeccion() {
    this(Clock.systemUTC());
  }

  ValidacionFechaInspeccion(Clock reloj) {
    this.reloj = reloj;
  }

  void exigir(LocalDate fecha, Integer anioConstruccion) {
    if (fecha == null) {
      throw campo("La fecha de inspección es obligatoria.");
    }
    LocalDate hoy = LocalDate.now(reloj.withZone(ZONA));
    if (fecha.isAfter(hoy)) {
      throw campo("La fecha de inspección no puede ser futura.");
    }
    if (anioConstruccion != null && fecha.getYear() < anioConstruccion) {
      throw campo("La fecha no puede ser anterior al año de construcción del puente.");
    }
  }

  private static NegocioException campo(String mensaje) {
    return new NegocioException(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "fecha-inspeccion-invalida",
            "Fecha de inspección inválida",
            mensaje)
        .conErrores(List.of(new ErrorCampo("fecha_inspeccion", mensaje)));
  }
}
