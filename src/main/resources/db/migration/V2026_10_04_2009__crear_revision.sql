-- RN-REV-01 a RN-REV-07. Una fila por ciclo de revisión: así queda el historial completo (RN-REV-06)
CREATE TABLE revision (
  id UUID PRIMARY KEY,
  inspeccion_id UUID NOT NULL REFERENCES inspeccion,
  revisor_id UUID NOT NULL REFERENCES usuario,
  veredicto VARCHAR(20) CHECK (veredicto IN ('APROBADA', 'CAMBIOS_SOLICITADOS', 'RECHAZADA')),
  observacion TEXT,
  calificacion NUMERIC(5, 2) CHECK (calificacion BETWEEN 0 AND 100),
  emitida_en TIMESTAMPTZ,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- RN-REV-04
  CHECK (veredicto IS NULL OR veredicto = 'APROBADA' OR observacion IS NOT NULL),
  CHECK ((veredicto IS NULL) = (emitida_en IS NULL))
);

CREATE INDEX revision_inspeccion_idx ON revision (inspeccion_id);
CREATE INDEX revision_revisor_idx ON revision (revisor_id);

CREATE TABLE observacion_anclada (
  id UUID PRIMARY KEY,
  revision_id UUID NOT NULL REFERENCES revision,
  elemento_ref VARCHAR(200) NOT NULL,
  texto TEXT NOT NULL,
  resuelta_en TIMESTAMPTZ,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX observacion_anclada_revision_idx ON observacion_anclada (revision_id);

COMMENT ON TABLE revision IS 'Ciclo de revisión de una inspección de estudiante (RN-REV-01)';
COMMENT ON COLUMN revision.id IS 'UUID v7';
COMMENT ON COLUMN revision.inspeccion_id IS 'Inspección revisada';
COMMENT ON COLUMN revision.revisor_id IS 'Catedrático revisor; nunca el autor (RN-REV-03)';
COMMENT ON COLUMN revision.veredicto IS 'Veredicto emitido; NULL mientras está EN_REVISION (RN-REV-04)';
COMMENT ON COLUMN revision.observacion IS 'Obligatoria para Cambios solicitados y Rechazada';
COMMENT ON COLUMN revision.calificacion IS 'Calificación 0–100, visible solo para autor y revisor (RN-REV-07, pendiente #13)';
COMMENT ON COLUMN revision.emitida_en IS 'Cuándo se emitió el veredicto';
COMMENT ON COLUMN revision.creado_en IS 'Inicio de la revisión, en UTC';
COMMENT ON COLUMN revision.actualizado_en IS 'Última modificación, en UTC';

COMMENT ON TABLE observacion_anclada IS 'Observación del revisor anclada a un campo; genera un pendiente (RN-REV-05)';
COMMENT ON COLUMN observacion_anclada.id IS 'UUID v7';
COMMENT ON COLUMN observacion_anclada.revision_id IS 'Ciclo de revisión';
COMMENT ON COLUMN observacion_anclada.elemento_ref IS 'Ruta del campo observado (contrato 2)';
COMMENT ON COLUMN observacion_anclada.texto IS 'Observación';
COMMENT ON COLUMN observacion_anclada.resuelta_en IS 'Cuándo el autor la marcó como resuelta';
COMMENT ON COLUMN observacion_anclada.creado_en IS 'Fecha de creación, en UTC';
COMMENT ON COLUMN observacion_anclada.actualizado_en IS 'Última modificación, en UTC';
