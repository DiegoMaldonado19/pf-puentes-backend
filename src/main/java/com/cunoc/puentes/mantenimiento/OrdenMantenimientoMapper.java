package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.mantenimiento.dto.OrdenMantenimientoDTO;
import org.mapstruct.Mapper;

/** Solo lectura: no convierte un DTO de consulta en una entidad modificable. */
@Mapper
interface OrdenMantenimientoMapper {

  OrdenMantenimientoDTO aDTO(OrdenMantenimiento orden);
}
