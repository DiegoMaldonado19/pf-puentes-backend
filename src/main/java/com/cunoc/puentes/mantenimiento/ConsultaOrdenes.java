package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.mantenimiento.dto.OrdenMantenimientoDTO;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Consulta interna; la futura fachada debe autorizar al actor y su acceso al puente. */
@Component
@Transactional(readOnly = true)
class ConsultaOrdenes {

  private final OrdenMantenimientoRepository ordenes;
  private final OrdenMantenimientoMapper mapper;
  private final ValidacionOrigenOrden origen;

  ConsultaOrdenes(
      OrdenMantenimientoRepository ordenes,
      OrdenMantenimientoMapper mapper,
      ValidacionOrigenOrden origen) {
    this.ordenes = ordenes;
    this.mapper = mapper;
    this.origen = origen;
  }

  Page<OrdenMantenimientoDTO> listar(UUID puenteId, int pagina, int tamano) {
    if (pagina < 0 || tamano < 1 || tamano > 100 || (long) pagina * tamano > Integer.MAX_VALUE) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "pagina-de-ordenes-invalida",
          "Paginación inválida",
          "La página debe ser no negativa, con tamaño entre 1 y 100 y dentro del límite admitido.");
    }
    origen.validar(puenteId, null);
    return ordenes
        .findByPuenteId(puenteId, PageRequest.of(pagina, tamano, Sort.by("id").descending()))
        .map(mapper::aDTO);
  }

  OrdenMantenimientoDTO obtener(UUID puenteId, UUID ordenId) {
    if (puenteId == null || ordenId == null) {
      throw new IllegalArgumentException("El puente y la orden son obligatorios.");
    }
    return ordenes
        .findByIdAndPuenteId(ordenId, puenteId)
        .map(mapper::aDTO)
        .orElseThrow(
            () ->
                new NegocioException(
                    HttpStatus.NOT_FOUND,
                    "orden-no-encontrada",
                    "Orden no encontrada",
                    "La orden indicada no existe en el puente solicitado."));
  }
}
