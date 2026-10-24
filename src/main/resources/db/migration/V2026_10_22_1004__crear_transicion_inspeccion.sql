-- RN-INS-04, RN-INS-13, RN-REV-06, pendientes #2 y #3: historial de cambios de estado de cada inspección
CREATE TABLE transicion_inspeccion (
  id UUID PRIMARY KEY,
  inspeccion_id UUID NOT NULL REFERENCES inspeccion,
  estado_origen VARCHAR(20)
    CHECK (estado_origen IN ('BORRADOR', 'ENVIADA', 'EN_REVISION', 'CAMBIOS_SOLICITADOS', 'PUBLICADA', 'RECHAZADA')),
  estado_destino VARCHAR(20) NOT NULL
    CHECK (estado_destino IN ('BORRADOR', 'ENVIADA', 'EN_REVISION', 'CAMBIOS_SOLICITADOS', 'PUBLICADA', 'RECHAZADA')),
  actor_id UUID REFERENCES usuario,
  revision_id UUID REFERENCES revision,
  motivo VARCHAR(500),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (motivo IS NULL OR btrim(motivo) <> ''),
  -- RN-INS-04 según la lectura del equipo (pendientes #2, #3 y #4). Sin origen solo nace un BORRADOR
  CONSTRAINT transicion_inspeccion_valida_ck CHECK (
    (estado_origen IS NULL AND estado_destino = 'BORRADOR')
    OR (estado_origen IS NOT NULL AND (estado_origen, estado_destino) IN (
      ('BORRADOR', 'ENVIADA'),
      ('ENVIADA', 'EN_REVISION'),
      ('ENVIADA', 'PUBLICADA'),
      ('EN_REVISION', 'PUBLICADA'),
      ('EN_REVISION', 'CAMBIOS_SOLICITADOS'),
      ('EN_REVISION', 'RECHAZADA'),
      ('CAMBIOS_SOLICITADOS', 'BORRADOR')))
  )
);

-- Línea de tiempo de una inspección, en orden cronológico
CREATE INDEX transicion_inspeccion_historial_idx ON transicion_inspeccion (inspeccion_id, creado_en);

COMMENT ON TABLE transicion_inspeccion IS 'Historial de cambios de estado de una inspección: quién, cuándo y por qué. Solo se inserta (RN-INS-04, RN-REV-06)';
COMMENT ON COLUMN transicion_inspeccion.id IS 'UUID v7';
COMMENT ON COLUMN transicion_inspeccion.inspeccion_id IS 'Inspección que cambió de estado';
COMMENT ON COLUMN transicion_inspeccion.estado_origen IS 'Estado anterior; NULL en la creación de la inspección';
COMMENT ON COLUMN transicion_inspeccion.estado_destino IS 'Estado al que pasó';
COMMENT ON COLUMN transicion_inspeccion.actor_id IS 'Quién provocó el cambio; NULL si fue el Sistema (ACT-06)';
COMMENT ON COLUMN transicion_inspeccion.revision_id IS 'Ciclo de revisión que originó el cambio; NULL si no vino de una revisión';
COMMENT ON COLUMN transicion_inspeccion.motivo IS 'Justificación escrita, por ejemplo la observación de un rechazo';
COMMENT ON COLUMN transicion_inspeccion.creado_en IS 'Momento del cambio, en UTC';
COMMENT ON COLUMN transicion_inspeccion.actualizado_en IS 'Igual a creado_en: el historial no se modifica';
