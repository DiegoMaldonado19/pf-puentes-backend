package com.cunoc.puentes.indice;

import com.cunoc.puentes.indice.dto.CrearJuegoPesosDTO;
import com.cunoc.puentes.indice.dto.FormularioConPesosDTO;
import com.cunoc.puentes.indice.dto.JuegoPesosDTO;
import com.cunoc.puentes.indice.dto.PesoElementoDTO;
import com.cunoc.puentes.indice.dto.RecalculoIcDTO;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface PesosMapper {

  JuegoPesos aJuego(UUID formularioVersionId, CrearJuegoPesosDTO datos);

  PesoConfigurado aPesoConfigurado(CrearJuegoPesosDTO.DefinicionPesoDTO datos);

  PesoConfigurado aPesoConfigurado(PesoElemento peso);

  @Mapping(target = "formularioVersionId", source = "version.formularioVersionId")
  @Mapping(target = "acta", source = "version.acta")
  JuegoPesos aJuego(VersionPesos version, List<PesoElemento> pesos);

  FormularioConPesosDTO aDTO(VersionPesosRepository.FormularioConPesos formulario);

  JuegoPesosDTO aDTO(VersionPesos version);

  PesoElementoDTO aDTO(PesoElemento peso);

  @Mapping(target = "versionPesosId", source = "versionPesos.id")
  @Mapping(target = "formularioVersionId", source = "versionPesos.formularioVersionId")
  @Mapping(target = "acta", source = "versionPesos.acta")
  @Mapping(target = "vigenteDesde", source = "versionPesos.vigenteDesde")
  RecalculoIcDTO aDTO(RecalculoIc recalculo);

  @Mapping(target = "vigenteDesde", ignore = true)
  VersionPesos aVersion(JuegoPesos juego);

  @Mapping(target = "versionPesos", source = "version")
  PesoElemento aPeso(PesoConfigurado definicion, VersionPesos version);

  @Mapping(target = "inspeccionId", source = "inspeccionId")
  @Mapping(target = "versionPesos", source = "version")
  @Mapping(target = "indiceCondicion", source = "indiceCondicion")
  @Mapping(target = "estadoCalculado", source = "estadoCalculado")
  @Mapping(target = "anulacion", source = "anulacion")
  RecalculoIc aRecalculo(
      UUID inspeccionId,
      VersionPesos version,
      BigDecimal indiceCondicion,
      EstadoCondicion estadoCalculado,
      Anulacion anulacion);
}
