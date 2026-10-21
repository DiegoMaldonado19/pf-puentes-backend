package com.cunoc.puentes.puente;

import com.cunoc.puentes.puente.dto.SolicitudPuenteDTO;
import com.cunoc.puentes.puente.dto.UtmDTO;
import org.locationtech.jts.geom.Point;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(uses = CatalogoIneMapper.class, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
interface SolicitudPuenteMapper {

  @Mapping(target = "departamento", source = "municipio.departamento")
  @Mapping(target = "latitud", source = "ubicacion.y")
  @Mapping(target = "longitud", source = "ubicacion.x")
  @Mapping(target = "utm", source = "ubicacion")
  @Mapping(target = "puenteId", source = "puente.id")
  SolicitudPuenteDTO aDTO(SolicitudPuente solicitud);

  default UtmDTO utm(Point ubicacion) {
    return ConversorUtm.aUtm(ubicacion.getY(), ubicacion.getX());
  }
}
