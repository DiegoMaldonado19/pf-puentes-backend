-- RN-USR-09, DT-BD-12: la llena un aspecto en la capa de servicio, no un trigger
CREATE TABLE auditoria (
  id UUID PRIMARY KEY,
  usuario_id UUID REFERENCES usuario,
  accion VARCHAR(30) NOT NULL,
  entidad VARCHAR(50) NOT NULL,
  entidad_id UUID,
  valor_anterior JSONB,
  valor_nuevo JSONB,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX auditoria_entidad_idx ON auditoria (entidad, entidad_id);
CREATE INDEX auditoria_usuario_idx ON auditoria (usuario_id);

COMMENT ON TABLE auditoria IS 'Bitácora de toda acción sobre datos (RN-USR-09); sin contraseñas, tokens ni datos personales completos (DT-SEC-12)';
COMMENT ON COLUMN auditoria.id IS 'UUID v7';
COMMENT ON COLUMN auditoria.usuario_id IS 'Quién lo hizo; NULL si fue el Sistema (ACT-06)';
COMMENT ON COLUMN auditoria.accion IS 'Crear, modificar, cambiar de estado, moderar…';
COMMENT ON COLUMN auditoria.entidad IS 'Tabla afectada';
COMMENT ON COLUMN auditoria.entidad_id IS 'Id de la fila afectada';
COMMENT ON COLUMN auditoria.valor_anterior IS 'Valores antes de la acción';
COMMENT ON COLUMN auditoria.valor_nuevo IS 'Valores después de la acción';
COMMENT ON COLUMN auditoria.creado_en IS 'Momento de la acción, en UTC';
COMMENT ON COLUMN auditoria.actualizado_en IS 'Igual a creado_en: la bitácora no se modifica';
