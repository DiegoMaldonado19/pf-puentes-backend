-- DT-OFF-07 y RN-OFF-08: un reintento devuelve la respuesta de la operación original
CREATE TABLE idempotencia (
  id UUID PRIMARY KEY,
  usuario_id UUID NOT NULL REFERENCES usuario,
  clave VARCHAR(100) NOT NULL,
  estado_http INTEGER NOT NULL CHECK (estado_http BETWEEN 100 AND 599),
  respuesta JSONB,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (usuario_id, clave)
);

-- RN-OFF-09 y pendiente #18 (propuesta): en un conflicto gana el cliente y se guarda copia del servidor
CREATE TABLE copia_conflicto (
  id UUID PRIMARY KEY,
  inspeccion_id UUID NOT NULL REFERENCES inspeccion,
  datos JSONB NOT NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE idempotencia IS 'Respuestas guardadas por Idempotency-Key (DT-OFF-07)';
COMMENT ON COLUMN idempotencia.id IS 'UUID v7';
COMMENT ON COLUMN idempotencia.usuario_id IS 'Usuario que mandó la operación';
COMMENT ON COLUMN idempotencia.clave IS 'Valor del encabezado Idempotency-Key';
COMMENT ON COLUMN idempotencia.estado_http IS 'Código HTTP de la respuesta original';
COMMENT ON COLUMN idempotencia.respuesta IS 'Cuerpo de la respuesta original';
COMMENT ON COLUMN idempotencia.creado_en IS 'Fecha de la operación original, en UTC';
COMMENT ON COLUMN idempotencia.actualizado_en IS 'Igual a creado_en: la respuesta guardada no cambia';

COMMENT ON TABLE copia_conflicto IS 'Versión del servidor que pisó un cliente al sincronizar (RN-OFF-09)';
COMMENT ON COLUMN copia_conflicto.id IS 'UUID v7';
COMMENT ON COLUMN copia_conflicto.inspeccion_id IS 'Inspección en conflicto';
COMMENT ON COLUMN copia_conflicto.datos IS 'Datos que tenía el servidor antes de aplicar los del cliente';
COMMENT ON COLUMN copia_conflicto.creado_en IS 'Momento del conflicto, en UTC';
COMMENT ON COLUMN copia_conflicto.actualizado_en IS 'Igual a creado_en: la copia no cambia';
