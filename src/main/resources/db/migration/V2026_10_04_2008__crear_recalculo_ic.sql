-- RN-IC-08: un cambio de pesos no reescribe las publicadas; se guarda un recálculo fechado
CREATE TABLE recalculo_ic (
  id UUID PRIMARY KEY,
  inspeccion_id UUID NOT NULL REFERENCES inspeccion,
  version_pesos_id UUID NOT NULL REFERENCES version_pesos,
  indice_condicion NUMERIC(5, 2) NOT NULL CHECK (indice_condicion BETWEEN 0 AND 100),
  estado_calculado VARCHAR(10) NOT NULL CHECK (estado_calculado IN ('BUENO', 'REGULAR', 'MALO')),
  anulacion VARCHAR(20)
    CHECK (anulacion IN ('SOCAVACION', 'ASENTAMIENTO', 'PERDIDA_SECCION', 'AUSENCIA_PORTANTE')),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (inspeccion_id, version_pesos_id)
);

COMMENT ON TABLE recalculo_ic IS 'IC de una inspección publicada recalculado con otros pesos (RN-IC-08)';
COMMENT ON COLUMN recalculo_ic.id IS 'UUID v7';
COMMENT ON COLUMN recalculo_ic.inspeccion_id IS 'Inspección publicada';
COMMENT ON COLUMN recalculo_ic.version_pesos_id IS 'Pesos usados en el recálculo';
COMMENT ON COLUMN recalculo_ic.indice_condicion IS 'IC recalculado';
COMMENT ON COLUMN recalculo_ic.estado_calculado IS 'Estado recalculado';
COMMENT ON COLUMN recalculo_ic.anulacion IS 'Anulación aplicada; NULL si ninguna';
COMMENT ON COLUMN recalculo_ic.creado_en IS 'Fecha del recálculo, en UTC';
COMMENT ON COLUMN recalculo_ic.actualizado_en IS 'Igual a creado_en: un recálculo no se modifica';
