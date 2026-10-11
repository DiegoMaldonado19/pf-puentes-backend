-- RN-USR-09: motivo, correlación de operaciones y consultas por fecha
ALTER TABLE auditoria
  ADD COLUMN motivo VARCHAR(500),
  ADD COLUMN origen_ip VARCHAR(45),
  ADD COLUMN correlacion_id UUID;

ALTER TABLE auditoria
  ADD CONSTRAINT auditoria_motivo_ck
    CHECK (motivo IS NULL OR btrim(motivo) <> ''),
  -- NOT VALID: formato uniforme para lo que se escriba de ahora en adelante
  ADD CONSTRAINT auditoria_accion_ck
    CHECK (accion ~ '^[A-Z][A-Z0-9_]*$') NOT VALID,
  ADD CONSTRAINT auditoria_entidad_ck
    CHECK (entidad ~ '^[a-z][a-z0-9_]*$') NOT VALID;

-- Los índices originales no incluían la fecha; se reemplazan por versiones ordenadas por fecha
DROP INDEX auditoria_entidad_idx;
DROP INDEX auditoria_usuario_idx;

CREATE INDEX auditoria_entidad_fecha_idx ON auditoria (entidad, entidad_id, creado_en DESC);
CREATE INDEX auditoria_usuario_fecha_idx ON auditoria (usuario_id, creado_en DESC);
CREATE INDEX auditoria_fecha_idx ON auditoria (creado_en DESC);
CREATE INDEX auditoria_correlacion_idx ON auditoria (correlacion_id)
  WHERE correlacion_id IS NOT NULL;

COMMENT ON COLUMN auditoria.motivo IS 'Justificación escrita; obligatoria en moderación, bajas y rechazos (RN-FOR-08, RN-INV-08)';
COMMENT ON COLUMN auditoria.origen_ip IS 'Dirección IP de la petición; NULL si la acción fue del Sistema';
COMMENT ON COLUMN auditoria.correlacion_id IS 'Agrupa las filas generadas por una misma operación de negocio';
