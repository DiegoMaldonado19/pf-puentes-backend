package com.cunoc.puentes.usuario;

import com.cunoc.puentes.usuario.dto.CuentaRegistradaDTO;
import com.cunoc.puentes.usuario.dto.PerfilDTO;
import com.cunoc.puentes.usuario.dto.UsuarioAdminDTO;
import com.cunoc.puentes.usuario.dto.UsuarioSesionDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface UsuarioMapper {

  @Mapping(
      target = "correoVerificado",
      expression = "java(usuario.getCorreoVerificadoEn() != null)")
  CuentaRegistradaDTO aDTO(Usuario usuario);

  UsuarioSesionDTO aSesion(Usuario usuario);

  PerfilDTO aPerfil(Usuario usuario);

  @Mapping(
      target = "correoVerificado",
      expression = "java(usuario.getCorreoVerificadoEn() != null)")
  UsuarioAdminDTO aAdmin(Usuario usuario);
}
