package com.cunoc.puentes.foro;

import com.cunoc.puentes.foro.dto.ComentarioDTO;
import com.cunoc.puentes.foro.dto.HiloDTO;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper
interface ForoMapper {

  @Mapping(target = "cerrado", expression = "java(hilo.getCerradoEn() != null)")
  @Mapping(target = "creadoEn", source = "creadoEn", qualifiedByName = "normalizarFecha")
  @Mapping(target = "autorNombre", ignore = true)
  HiloDTO aDTO(Hilo hilo);

  @Mapping(target = "hiloId", source = "hilo.id")
  @Mapping(target = "padreId", source = "respuestaA.id")
  @Mapping(target = "eliminado", expression = "java(comentario.getEliminadoEn() != null)")
  @Mapping(
      target = "texto",
      expression = "java(comentario.getEliminadoEn() != null ? null : comentario.getTexto())")
  @Mapping(target = "creadoEn", source = "creadoEn", qualifiedByName = "normalizarFecha")
  @Mapping(target = "autorNombre", ignore = true)
  ComentarioDTO aDTO(Comentario comentario);

  @Named("normalizarFecha")
  default Instant normalizarFecha(Instant fecha) {
    return fecha != null ? fecha.truncatedTo(ChronoUnit.MICROS) : null;
  }
}
