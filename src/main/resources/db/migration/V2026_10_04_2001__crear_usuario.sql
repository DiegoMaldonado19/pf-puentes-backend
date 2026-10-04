-- RN-USR-01 a RN-USR-08, DT-SEC-01
CREATE TABLE usuario (
  id UUID PRIMARY KEY,
  correo VARCHAR(254) NOT NULL,
  nombre VARCHAR(150) NOT NULL,
  contrasena_hash VARCHAR(100) NOT NULL,
  rol VARCHAR(30) NOT NULL
    CHECK (rol IN ('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO', 'ADMINISTRADOR')),
  estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
    CHECK (estado IN ('PENDIENTE', 'ACTIVO', 'INACTIVO')),
  correo_verificado_en TIMESTAMPTZ,
  numero_colegiado VARCHAR(20),
  colegiado_verificado_en TIMESTAMPTZ,
  intentos_fallidos INTEGER NOT NULL DEFAULT 0 CHECK (intentos_fallidos >= 0),
  primer_fallo_en TIMESTAMPTZ,
  bloqueado_hasta TIMESTAMPTZ,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- RN-USR-04: el Profesional externo no se habilita sin colegiado verificado
  CHECK (rol <> 'PROFESIONAL_EXTERNO' OR numero_colegiado IS NOT NULL),
  CHECK (rol <> 'PROFESIONAL_EXTERNO' OR estado <> 'ACTIVO' OR colegiado_verificado_en IS NOT NULL)
);

CREATE UNIQUE INDEX usuario_correo_uk ON usuario (lower(correo));

CREATE TABLE token_renovacion (
  id UUID PRIMARY KEY,
  usuario_id UUID NOT NULL REFERENCES usuario,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expira_en TIMESTAMPTZ NOT NULL,
  revocado_en TIMESTAMPTZ,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX token_renovacion_usuario_idx ON token_renovacion (usuario_id);

CREATE TABLE invitacion (
  id UUID PRIMARY KEY,
  correo VARCHAR(254) NOT NULL,
  -- RN-USR-03: solo estos roles entran por invitación; el Estudiante se registra solo
  rol VARCHAR(30) NOT NULL CHECK (rol IN ('PROFESIONAL_EXTERNO', 'CATEDRATICO', 'ADMINISTRADOR')),
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expira_en TIMESTAMPTZ NOT NULL,
  aceptada_en TIMESTAMPTZ,
  administrador_id UUID NOT NULL REFERENCES usuario,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE usuario IS 'Cuentas del sistema; nunca se borran, se desactivan (RN-USR-08)';
COMMENT ON COLUMN usuario.id IS 'UUID v7';
COMMENT ON COLUMN usuario.correo IS 'Correo de acceso; único sin distinguir mayúsculas';
COMMENT ON COLUMN usuario.nombre IS 'Nombre completo';
COMMENT ON COLUMN usuario.contrasena_hash IS 'Hash BCrypt, factor de trabajo 12 o más (RN-USR-06)';
COMMENT ON COLUMN usuario.rol IS 'Rol global único (RN-USR-05)';
COMMENT ON COLUMN usuario.estado IS 'PENDIENTE hasta que lo activen (RN-USR-02); INACTIVO es la baja lógica';
COMMENT ON COLUMN usuario.correo_verificado_en IS 'Cuándo verificó su correo (RN-USR-01)';
COMMENT ON COLUMN usuario.numero_colegiado IS 'Número de colegiado del Profesional externo';
COMMENT ON COLUMN usuario.colegiado_verificado_en IS 'Cuándo el Administrador verificó el colegiado (RN-USR-04)';
COMMENT ON COLUMN usuario.intentos_fallidos IS 'Intentos fallidos de inicio de sesión en la ventana actual (RN-USR-07)';
COMMENT ON COLUMN usuario.primer_fallo_en IS 'Inicio de la ventana de 15 minutos de intentos fallidos';
COMMENT ON COLUMN usuario.bloqueado_hasta IS 'Fin del bloqueo temporal';
COMMENT ON COLUMN usuario.creado_en IS 'Fecha de creación, en UTC';
COMMENT ON COLUMN usuario.actualizado_en IS 'Última modificación, en UTC';

COMMENT ON TABLE token_renovacion IS 'Tokens de renovación emitidos, para poder revocarlos (DT-SEC-01)';
COMMENT ON COLUMN token_renovacion.id IS 'UUID v7';
COMMENT ON COLUMN token_renovacion.usuario_id IS 'Dueño del token';
COMMENT ON COLUMN token_renovacion.token_hash IS 'SHA-256 del token; nunca el valor';
COMMENT ON COLUMN token_renovacion.expira_en IS 'Vence a los 7 días';
COMMENT ON COLUMN token_renovacion.revocado_en IS 'Cuándo se revocó; NULL si sigue vigente';
COMMENT ON COLUMN token_renovacion.creado_en IS 'Fecha de emisión, en UTC';
COMMENT ON COLUMN token_renovacion.actualizado_en IS 'Última modificación, en UTC';

COMMENT ON TABLE invitacion IS 'Invitaciones del Administrador para roles que no se auto-registran (RN-USR-03)';
COMMENT ON COLUMN invitacion.id IS 'UUID v7';
COMMENT ON COLUMN invitacion.correo IS 'Correo invitado';
COMMENT ON COLUMN invitacion.rol IS 'Rol que tendrá la cuenta';
COMMENT ON COLUMN invitacion.token_hash IS 'SHA-256 del token del enlace de invitación';
COMMENT ON COLUMN invitacion.expira_en IS 'Vencimiento del enlace';
COMMENT ON COLUMN invitacion.aceptada_en IS 'Cuándo se creó la cuenta; NULL si sigue pendiente';
COMMENT ON COLUMN invitacion.administrador_id IS 'Administrador que invitó';
COMMENT ON COLUMN invitacion.creado_en IS 'Fecha de envío, en UTC';
COMMENT ON COLUMN invitacion.actualizado_en IS 'Última modificación, en UTC';
