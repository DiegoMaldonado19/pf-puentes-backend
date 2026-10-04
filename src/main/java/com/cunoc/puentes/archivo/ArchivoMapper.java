package com.cunoc.puentes.archivo;

import com.cunoc.puentes.archivo.dto.ArchivoDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface ArchivoMapper {

  @Mapping(target = "latitud", source = "archivo.ubicacion.y")
  @Mapping(target = "longitud", source = "archivo.ubicacion.x")
  ArchivoDTO aDTO(Archivo archivo, String url, String urlMiniatura);
}
