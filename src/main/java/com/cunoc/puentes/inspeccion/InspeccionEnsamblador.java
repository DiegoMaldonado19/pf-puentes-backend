package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.inspeccion.dto.AcompananteDTO;
import com.cunoc.puentes.inspeccion.dto.FormularioResumenDTO;
import com.cunoc.puentes.inspeccion.dto.InspeccionDTO;
import com.cunoc.puentes.inspeccion.dto.InspeccionResumenDTO;
import com.cunoc.puentes.inspeccion.dto.PuenteResumenDTO;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

@Component
class InspeccionEnsamblador {

  static final String GPS_NO_DISPONIBLE = "GPS no disponible";

  private final DistanciaAlPuente distancias;

  InspeccionEnsamblador(DistanciaAlPuente distancias) {
    this.distancias = distancias;
  }

  InspeccionResumenDTO resumen(
      Inspeccion inspeccion, ReferenciasInspeccion.PuenteVista puente, UUID superadaPor) {
    return new InspeccionResumenDTO(
        inspeccion.getId(),
        puente(puente, inspeccion.getPuenteId()),
        inspeccion.getEstado(),
        inspeccion.getFechaInspeccion(),
        inspeccion.getCursoId(),
        inspeccion.getCreadoEn(),
        superadaPor != null,
        superadaPor);
  }

  InspeccionDTO detalle(
      Inspeccion inspeccion,
      ReferenciasInspeccion.PuenteVista puente,
      String codigoFormulario,
      List<AcompananteDTO> acompanantes,
      UUID superadaPor,
      List<String> advertencias) {
    boolean publicada = inspeccion.getEstado() == EstadoInspeccion.PUBLICADA;
    Gps gps = gps(inspeccion);
    return new InspeccionDTO(
        inspeccion.getId(),
        puente(puente, inspeccion.getPuenteId()),
        new FormularioResumenDTO(inspeccion.getFormularioVersionId(), codigoFormulario),
        inspeccion.getEstado(),
        inspeccion.getFechaInspeccion(),
        inspeccion.getCursoId(),
        inspeccion.getAutorId(),
        inspeccion.getDispositivoId(),
        inspeccion.getCreadaEnDispositivo(),
        inspeccion.getCreadoEn(),
        inspeccion.getEnviadaEn(),
        inspeccion.getSincronizadaEn(),
        inspeccion.getPublicadaEn(),
        gps.disponible(),
        gps.latitud(),
        gps.longitud(),
        inspeccion.getDistanciaAPuenteM(),
        gps.advertencia(),
        Map.copyOf(inspeccion.getDatos() == null ? Map.of() : inspeccion.getDatos()),
        List.copyOf(acompanantes),
        publicada ? inspeccion.getIndiceCondicion() : null,
        publicada ? inspeccion.getEstadoCalculado() : null,
        publicada ? inspeccion.getEstadoConfirmado() : null,
        publicada ? inspeccion.getJustificacionEstado() : null,
        superadaPor != null,
        superadaPor,
        inspeccion.getSupersedeA() == null ? null : inspeccion.getSupersedeA().getId(),
        inspeccion.getMotivoCorreccion(),
        List.copyOf(advertencias == null ? List.of() : advertencias));
  }

  private PuenteResumenDTO puente(ReferenciasInspeccion.PuenteVista puente, UUID puenteId) {
    if (puente == null) {
      return new PuenteResumenDTO(puenteId, null, null);
    }
    return new PuenteResumenDTO(puente.id(), puente.codigo(), puente.nombre());
  }

  private Gps gps(Inspeccion inspeccion) {
    Point punto = inspeccion.getUbicacionInicio();
    if (punto == null) {
      return new Gps(false, null, null, GPS_NO_DISPONIBLE);
    }
    BigDecimal metros = inspeccion.getDistanciaAPuenteM();
    String advertencia = distancias.lejana(metros) ? distancias.advertencia(metros) : null;
    return new Gps(true, punto.getY(), punto.getX(), advertencia);
  }

  private record Gps(boolean disponible, Double latitud, Double longitud, String advertencia) {}
}
