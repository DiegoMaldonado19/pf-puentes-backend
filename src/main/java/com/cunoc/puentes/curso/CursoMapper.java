package com.cunoc.puentes.curso;

import com.cunoc.puentes.curso.dto.AsignacionDTO;
import com.cunoc.puentes.curso.dto.CursoDTO;
import com.cunoc.puentes.curso.dto.GuardarCursoDTO;
import com.cunoc.puentes.curso.dto.InscripcionDTO;
import com.cunoc.puentes.curso.dto.PuenteDisponibleDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface CursoMapper {
  CursoDTO aDTO(Curso curso);

  @BeanMapping(ignoreByDefault = true)
  @Mapping(target = "nombre", expression = "java(datos.nombre().strip())")
  @Mapping(target = "anio", source = "anio")
  @Mapping(target = "semestre", source = "semestre")
  @Mapping(target = "vigente", source = "vigente")
  void actualizar(GuardarCursoDTO datos, @MappingTarget Curso curso);

  @Mapping(target = "cursoId", source = "inscripcion.curso.id")
  InscripcionDTO aDTO(Inscripcion inscripcion, String estudianteNombre);

  @Mapping(target = "id", source = "asignacion.id")
  @Mapping(target = "inscripcionId", source = "asignacion.inscripcion.id")
  @Mapping(target = "cursoId", source = "asignacion.inscripcion.curso.id")
  @Mapping(target = "cursoNombre", source = "asignacion.inscripcion.curso.nombre")
  @Mapping(target = "estudianteId", source = "asignacion.inscripcion.estudianteId")
  @Mapping(target = "puenteCodigo", source = "puente.codigo")
  @Mapping(target = "puenteNombre", source = "puente.nombre")
  AsignacionDTO aDTO(Asignacion asignacion, PuenteDisponibleDTO puente);
}
