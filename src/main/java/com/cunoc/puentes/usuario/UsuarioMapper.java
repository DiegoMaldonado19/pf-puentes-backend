package com.cunoc.puentes.usuario;

import com.cunoc.puentes.usuario.dto.CuentaRegistradaDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface UsuarioMapper {

  @Mapping(
      target = "correoVerificado",
      expression = "java(usuario.getCorreoVerificadoEn() != null)")
  CuentaRegistradaDTO aDTO(Usuario usuario);
}
