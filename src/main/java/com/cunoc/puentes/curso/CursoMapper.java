package com.cunoc.puentes.curso;

import com.cunoc.puentes.curso.dto.AsignacionDTO;
import com.cunoc.puentes.curso.dto.CursoDTO;
import com.cunoc.puentes.curso.dto.GuardarCursoDTO;
import com.cunoc.puentes.curso.dto.InscripcionDTO;
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

  @Mapping(target = "cursoId", source = "curso.id")
  InscripcionDTO aDTO(Inscripcion inscripcion);

  @Mapping(target = "inscripcionId", source = "inscripcion.id")
  @Mapping(target = "cursoId", source = "inscripcion.curso.id")
  @Mapping(target = "estudianteId", source = "inscripcion.estudianteId")
  AsignacionDTO aDTO(Asignacion asignacion);
}
