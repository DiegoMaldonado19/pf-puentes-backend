-- RN-ARC-06: actividad real del autor y aviso de abandono a los 180 días
ALTER TABLE inspeccion
  ADD COLUMN ultima_actividad_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  ADD COLUMN aviso_abandono_en TIMESTAMPTZ;

-- Las filas existentes toman su última modificación, no la hora de la migración
UPDATE inspeccion SET ultima_actividad_en = actualizado_en;

-- Búsqueda diaria de borradores inactivos sin recorrer toda la tabla
CREATE INDEX inspeccion_borrador_actividad_idx ON inspeccion (ultima_actividad_en)
  WHERE estado = 'BORRADOR';

COMMENT ON COLUMN inspeccion.ultima_actividad_en IS 'Última actividad del autor sobre el borrador (guardado, foto, documento); no la tocan los procesos del sistema (RN-ARC-06)';
COMMENT ON COLUMN inspeccion.aviso_abandono_en IS 'Cuándo se avisó al autor de que el borrador lleva 180 días inactivo; NULL si no se ha avisado (RN-ARC-06)';
