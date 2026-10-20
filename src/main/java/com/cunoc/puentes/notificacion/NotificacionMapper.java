package com.cunoc.puentes.notificacion;

import com.cunoc.puentes.notificacion.dto.NotificacionDTO;
import org.mapstruct.Mapper;

@Mapper
interface NotificacionMapper {

  NotificacionDTO aDTO(Notificacion notificacion);
}
