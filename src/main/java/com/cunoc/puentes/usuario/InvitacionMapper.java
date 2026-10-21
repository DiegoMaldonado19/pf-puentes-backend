package com.cunoc.puentes.usuario;

import com.cunoc.puentes.usuario.dto.InvitacionDTO;
import com.cunoc.puentes.usuario.dto.InvitacionPublicaDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface InvitacionMapper {

  @Mapping(target = "id", source = "invitacion.id")
  @Mapping(target = "creadoEn", source = "invitacion.creadoEn")
  @Mapping(target = "administradorId", source = "invitacion.administrador.id")
  @Mapping(target = "administradorNombre", source = "invitacion.administrador.nombre")
  @Mapping(target = "usuarioId", source = "invitacion.usuario.id")
  InvitacionDTO aDTO(Invitacion invitacion, EstadoInvitacion estado);

  InvitacionPublicaDTO aPublica(Invitacion invitacion);
}
