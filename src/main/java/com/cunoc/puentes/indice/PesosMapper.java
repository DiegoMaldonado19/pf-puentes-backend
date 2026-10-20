package com.cunoc.puentes.indice;

import com.cunoc.puentes.indice.dto.JuegoPesosDTO;
import com.cunoc.puentes.indice.dto.PesoElementoDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface PesosMapper {

  JuegoPesosDTO aDTO(VersionPesos version);

  PesoElementoDTO aDTO(PesoElemento peso);

  @Mapping(target = "vigenteDesde", ignore = true)
  VersionPesos aVersion(JuegoPesos juego);

  @Mapping(target = "versionPesos", source = "version")
  PesoElemento aPeso(PesoConfigurado definicion, VersionPesos version);
}
