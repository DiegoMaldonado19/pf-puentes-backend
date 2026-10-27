-- RN-REV-08: entrega única por envío y destinatario, atómica con la notificación.
CREATE TABLE aviso_revision (
  inspeccion_id UUID NOT NULL REFERENCES inspeccion,
  enviada_en TIMESTAMPTZ NOT NULL,
  destinatario_id UUID NOT NULL REFERENCES usuario,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (inspeccion_id, enviada_en, destinatario_id)
);
COMMENT ON TABLE aviso_revision IS 'Registro idempotente del aviso de revisión vencida de cada envío (RN-REV-08)';
COMMENT ON COLUMN aviso_revision.inspeccion_id IS 'Inspección enviada sin veredicto';
COMMENT ON COLUMN aviso_revision.enviada_en IS 'Identifica el envío; cambia al reenviar y conserva avisos anteriores';
COMMENT ON COLUMN aviso_revision.destinatario_id IS 'Catedrático responsable al emitir la notificación';
COMMENT ON COLUMN aviso_revision.creado_en IS 'Fecha del registro del aviso, UTC';
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'puentes_app') THEN
    GRANT SELECT, INSERT ON aviso_revision TO puentes_app;
    REVOKE UPDATE, DELETE ON aviso_revision FROM puentes_app;
  END IF;
END
$$;
