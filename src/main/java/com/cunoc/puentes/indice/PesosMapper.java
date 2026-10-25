package com.cunoc.puentes.indice;

import com.cunoc.puentes.indice.dto.CrearJuegoPesosDTO;
import com.cunoc.puentes.indice.dto.FormularioConPesosDTO;
import com.cunoc.puentes.indice.dto.JuegoPesosDTO;
import com.cunoc.puentes.indice.dto.PesoElementoDTO;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface PesosMapper {

  JuegoPesos aJuego(UUID formularioVersionId, CrearJuegoPesosDTO datos);

  PesoConfigurado aPesoConfigurado(CrearJuegoPesosDTO.DefinicionPesoDTO datos);

  FormularioConPesosDTO aDTO(VersionPesosRepository.FormularioConPesos formulario);

  JuegoPesosDTO aDTO(VersionPesos version);

  PesoElementoDTO aDTO(PesoElemento peso);

  @Mapping(target = "vigenteDesde", ignore = true)
  VersionPesos aVersion(JuegoPesos juego);

  @Mapping(target = "versionPesos", source = "version")
  PesoElemento aPeso(PesoConfigurado definicion, VersionPesos version);
}
