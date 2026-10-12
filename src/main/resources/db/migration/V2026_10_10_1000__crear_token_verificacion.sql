-- RN-USR-01 (correo verificado) y DT-SEC-07 / pendiente #9 (recuperación de contraseña)
CREATE TABLE token_verificacion (
  id UUID PRIMARY KEY,
  usuario_id UUID NOT NULL REFERENCES usuario,
  tipo VARCHAR(30) NOT NULL
    CHECK (tipo IN ('VERIFICACION_CORREO', 'RECUPERACION_CONTRASENA')),
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expira_en TIMESTAMPTZ NOT NULL,
  usado_en TIMESTAMPTZ,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (expira_en > creado_en)
);

-- Buscar y anular los tokens anteriores de un usuario al emitir uno nuevo
CREATE INDEX token_verificacion_usuario_idx ON token_verificacion (usuario_id, tipo);

-- Depuración de vencidos sin recorrer toda la tabla (predicado inmutable)
CREATE INDEX token_verificacion_pendiente_idx ON token_verificacion (expira_en)
  WHERE usado_en IS NULL;

COMMENT ON TABLE token_verificacion IS 'Tokens de un solo uso enviados por correo: verificar el correo (RN-USR-01) o recuperar la contraseña (DT-SEC-07)';
COMMENT ON COLUMN token_verificacion.id IS 'UUID v7';
COMMENT ON COLUMN token_verificacion.usuario_id IS 'Cuenta a la que pertenece el token';
COMMENT ON COLUMN token_verificacion.tipo IS 'VERIFICACION_CORREO o RECUPERACION_CONTRASENA';
COMMENT ON COLUMN token_verificacion.token_hash IS 'SHA-256 del token del enlace; nunca el valor (DT-SEC-12)';
COMMENT ON COLUMN token_verificacion.expira_en IS 'Vencimiento del enlace';
COMMENT ON COLUMN token_verificacion.usado_en IS 'Cuándo se usó; NULL si sigue sin usar. Un token usado no se reutiliza';
COMMENT ON COLUMN token_verificacion.creado_en IS 'Fecha de emisión, en UTC';
COMMENT ON COLUMN token_verificacion.actualizado_en IS 'Última modificación, en UTC';
