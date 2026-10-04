-- Contrato 5 y pendiente #8: notificaciones dentro de la app
CREATE TABLE notificacion (
  id UUID PRIMARY KEY,
  usuario_id UUID NOT NULL REFERENCES usuario,
  tipo VARCHAR(50) NOT NULL,
  mensaje TEXT NOT NULL,
  enlace VARCHAR(300),
  leida_en TIMESTAMPTZ,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX notificacion_usuario_idx ON notificacion (usuario_id);

COMMENT ON TABLE notificacion IS 'Notificaciones de la campana (contrato 5)';
COMMENT ON COLUMN notificacion.id IS 'UUID v7';
COMMENT ON COLUMN notificacion.usuario_id IS 'Destinatario';
COMMENT ON COLUMN notificacion.tipo IS 'Origen: mención, alerta de puente Malo, revisión pendiente…';
COMMENT ON COLUMN notificacion.mensaje IS 'Texto que ve el usuario';
COMMENT ON COLUMN notificacion.enlace IS 'Ruta de la app a la que lleva';
COMMENT ON COLUMN notificacion.leida_en IS 'Cuándo la leyó; NULL si no la ha leído';
COMMENT ON COLUMN notificacion.creado_en IS 'Fecha de creación, en UTC';
COMMENT ON COLUMN notificacion.actualizado_en IS 'Última modificación, en UTC';
