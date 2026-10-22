package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.mantenimiento.dto.PaginaOrdenesDTO;
import com.cunoc.puentes.mantenimiento.dto.SeguimientoRutinarioDTO;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class SeguimientoRutinarioService {
  private final SeguimientoRutinarioRepository seguimiento;
  private final OrdenMantenimientoRepository ordenes;
  private final OrdenMantenimientoMapper mapper;
  private final Clock reloj;

  SeguimientoRutinarioService(
      SeguimientoRutinarioRepository seguimiento,
      OrdenMantenimientoRepository ordenes,
      OrdenMantenimientoMapper mapper,
      @Qualifier("relojMantenimiento") Clock reloj) {
    this.seguimiento = seguimiento;
    this.ordenes = ordenes;
    this.mapper = mapper;
    this.reloj = reloj;
  }

  public SeguimientoRutinarioDTO consultar(UUID puenteId, int pagina, int tamano) {
    if (puenteId == null) throw new IllegalArgumentException("El puente es obligatorio.");
    if (pagina < 0 || tamano < 1 || tamano > 100 || (long) pagina * tamano > Integer.MAX_VALUE) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "pagina-de-ordenes-invalida",
          "Paginación inválida",
          "Use página no negativa y tamaño entre 1 y 100 dentro del límite admitido.");
    }
    var base =
        seguimiento
            .base(puenteId)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "puente-no-encontrado",
                        "Puente no encontrado",
                        "El puente solicitado no existe."));
    LocalDate hoy = LocalDate.now(reloj);
    LocalDate referencia = base.ejecucion() == null ? base.registro() : base.ejecucion();
    LocalDate limite = referencia.plusYears(1);
    var conteos = seguimiento.contar(puenteId, hoy, limite);
    var paginaOrdenes =
        ordenes
            .findByPuenteIdAndTipoAndEstadoIn(
                puenteId,
                TipoOrden.RUTINARIO,
                List.of(EstadoOrden.PROGRAMADA, EstadoOrden.EN_EJECUCION),
                PageRequest.of(pagina, tamano, Sort.by("id").descending()))
            .map(mapper::aDTO);
    return new SeguimientoRutinarioDTO(
        puenteId,
        hoy,
        referencia,
        base.ejecucion() == null ? "REGISTRO_PUENTE" : "EJECUCION_RUTINARIA",
        limite,
        conteos.enPlazo(),
        conteos.sinFecha(),
        conteos.pasadas(),
        conteos.posteriores(),
        PaginaOrdenesDTO.de(puenteId, paginaOrdenes));
  }
}
