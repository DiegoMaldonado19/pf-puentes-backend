package com.cunoc.puentes.common.auditoria;

import com.cunoc.puentes.common.auditoria.dto.AuditoriaDTO;
import org.mapstruct.Mapper;

@Mapper
interface AuditoriaMapper {

  AuditoriaDTO aDTO(Auditoria auditoria);
}
