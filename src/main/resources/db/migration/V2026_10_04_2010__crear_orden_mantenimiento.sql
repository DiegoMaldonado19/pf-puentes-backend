-- RN-MTO-01 a RN-MTO-09
CREATE TABLE orden_mantenimiento (
  id UUID PRIMARY KEY,
  puente_id UUID NOT NULL REFERENCES puente,
  inspeccion_id UUID REFERENCES inspeccion,
  tipo VARCHAR(15) NOT NULL CHECK (tipo IN ('RUTINARIO', 'PREVENTIVO', 'CORRECTIVO', 'EMERGENCIA')),
  estado VARCHAR(15) NOT NULL DEFAULT 'PROPUESTA'
    CHECK (estado IN ('PROPUESTA', 'PROGRAMADA', 'EN_EJECUCION', 'EJECUTADA', 'DESCARTADA', 'CANCELADA')),
  descripcion TEXT NOT NULL,
  elemento_ref VARCHAR(200),
  prioridad_sugerida VARCHAR(5) CHECK (prioridad_sugerida IN ('ALTA', 'MEDIA', 'BAJA')),
  prioridad VARCHAR(5) NOT NULL CHECK (prioridad IN ('ALTA', 'MEDIA', 'BAJA')),
  justificacion_prioridad TEXT,
  fecha_programada DATE,
  fecha_ejecucion DATE,
  responsable VARCHAR(150),
  motivo TEXT,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- RN-MTO-06
  CHECK (prioridad_sugerida IS NULL OR prioridad = prioridad_sugerida OR justificacion_prioridad IS NOT NULL),
  -- RN-MTO-05: la foto de evidencia la valida el servicio (está en archivo)
  CHECK (estado <> 'EJECUTADA' OR (fecha_ejecucion IS NOT NULL AND responsable IS NOT NULL)),
  -- RN-MTO-09
  CHECK (estado NOT IN ('DESCARTADA', 'CANCELADA') OR motivo IS NOT NULL)
);

CREATE INDEX orden_mantenimiento_puente_idx ON orden_mantenimiento (puente_id);

COMMENT ON TABLE orden_mantenimiento IS 'Orden de mantenimiento con su ciclo de vida (RN-MTO-04); nunca se borra (RN-MTO-09)';
COMMENT ON COLUMN orden_mantenimiento.id IS 'UUID v7';
COMMENT ON COLUMN orden_mantenimiento.puente_id IS 'Puente (RN-MTO-01)';
COMMENT ON COLUMN orden_mantenimiento.inspeccion_id IS 'Inspección que la originó, si la hay (RN-MTO-01)';
COMMENT ON COLUMN orden_mantenimiento.tipo IS 'Tipología del manual (RN-MTO-03)';
COMMENT ON COLUMN orden_mantenimiento.estado IS 'Estado de la máquina de RN-MTO-04';
COMMENT ON COLUMN orden_mantenimiento.descripcion IS 'Intervención a realizar';
COMMENT ON COLUMN orden_mantenimiento.elemento_ref IS 'Elemento afectado (contrato 2)';
COMMENT ON COLUMN orden_mantenimiento.prioridad_sugerida IS 'Sugerida por el IC y el peso del elemento (RN-MTO-06, pendiente #13)';
COMMENT ON COLUMN orden_mantenimiento.prioridad IS 'Prioridad vigente';
COMMENT ON COLUMN orden_mantenimiento.justificacion_prioridad IS 'Obligatoria si la prioridad difiere de la sugerida';
COMMENT ON COLUMN orden_mantenimiento.fecha_programada IS 'Fecha programada';
COMMENT ON COLUMN orden_mantenimiento.fecha_ejecucion IS 'Fecha de ejecución (RN-MTO-05)';
COMMENT ON COLUMN orden_mantenimiento.responsable IS 'Responsable de la ejecución (RN-MTO-05)';
COMMENT ON COLUMN orden_mantenimiento.motivo IS 'Motivo de descarte o cancelación (RN-MTO-09)';
COMMENT ON COLUMN orden_mantenimiento.creado_en IS 'Fecha de creación, en UTC';
COMMENT ON COLUMN orden_mantenimiento.actualizado_en IS 'Última modificación, en UTC';
