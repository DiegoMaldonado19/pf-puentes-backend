package com.cunoc.puentes.indice;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface PesosMapper {

  @Mapping(target = "vigenteDesde", ignore = true)
  VersionPesos aVersion(JuegoPesos juego);

  @Mapping(target = "versionPesos", source = "version")
  PesoElemento aPeso(PesoConfigurado definicion, VersionPesos version);
}
