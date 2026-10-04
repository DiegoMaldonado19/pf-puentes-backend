-- Pendiente #7 (propuesta, por confirmar con el catedrático): curso = catedrático + ciclo + estudiantes + puentes asignados
CREATE TABLE curso (
  id UUID PRIMARY KEY,
  nombre VARCHAR(150) NOT NULL,
  anio INTEGER NOT NULL CHECK (anio BETWEEN 2020 AND 2100),
  semestre INTEGER NOT NULL CHECK (semestre IN (1, 2)),
  catedratico_id UUID NOT NULL REFERENCES usuario,
  vigente BOOLEAN NOT NULL DEFAULT TRUE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE inscripcion (
  id UUID PRIMARY KEY,
  curso_id UUID NOT NULL REFERENCES curso,
  estudiante_id UUID NOT NULL REFERENCES usuario,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (curso_id, estudiante_id)
);

CREATE INDEX inscripcion_estudiante_idx ON inscripcion (estudiante_id);

-- Contrato 6: puentes asignados a un estudiante en un curso (RN-INS-14)
CREATE TABLE asignacion (
  id UUID PRIMARY KEY,
  inscripcion_id UUID NOT NULL REFERENCES inscripcion,
  puente_id UUID NOT NULL REFERENCES puente,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (inscripcion_id, puente_id)
);

COMMENT ON TABLE curso IS 'Curso de Puentes de un ciclo, dirigido por un Catedrático';
COMMENT ON COLUMN curso.id IS 'UUID v7';
COMMENT ON COLUMN curso.nombre IS 'Nombre o sección del curso';
COMMENT ON COLUMN curso.anio IS 'Año del ciclo';
COMMENT ON COLUMN curso.semestre IS 'Semestre del ciclo: 1 o 2';
COMMENT ON COLUMN curso.catedratico_id IS 'Catedrático que lo dirige (DT-SEC-04)';
COMMENT ON COLUMN curso.vigente IS 'TRUE mientras el ciclo esté en curso (RN-INS-14)';
COMMENT ON COLUMN curso.creado_en IS 'Fecha de creación, en UTC';
COMMENT ON COLUMN curso.actualizado_en IS 'Última modificación, en UTC';

COMMENT ON TABLE inscripcion IS 'Estudiante vinculado a un curso; activarlo y vincularlo es RN-USR-02';
COMMENT ON COLUMN inscripcion.id IS 'UUID v7';
COMMENT ON COLUMN inscripcion.curso_id IS 'Curso';
COMMENT ON COLUMN inscripcion.estudiante_id IS 'Estudiante inscrito';
COMMENT ON COLUMN inscripcion.creado_en IS 'Fecha de inscripción, en UTC';
COMMENT ON COLUMN inscripcion.actualizado_en IS 'Última modificación, en UTC';

COMMENT ON TABLE asignacion IS 'Puente que un estudiante puede inspeccionar dentro de su curso (RN-INS-14)';
COMMENT ON COLUMN asignacion.id IS 'UUID v7';
COMMENT ON COLUMN asignacion.inscripcion_id IS 'Inscripción del estudiante en el curso';
COMMENT ON COLUMN asignacion.puente_id IS 'Puente asignado';
COMMENT ON COLUMN asignacion.creado_en IS 'Fecha de asignación, en UTC';
COMMENT ON COLUMN asignacion.actualizado_en IS 'Última modificación, en UTC';
