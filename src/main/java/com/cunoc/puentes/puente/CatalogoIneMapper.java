package com.cunoc.puentes.puente;

import com.cunoc.puentes.puente.dto.DepartamentoDTO;
import com.cunoc.puentes.puente.dto.MunicipioDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface CatalogoIneMapper {

  DepartamentoDTO aDTO(Departamento departamento);

  @Mapping(target = "departamentoCodigo", source = "departamento.codigo")
  MunicipioDTO aDTO(Municipio municipio);
}
