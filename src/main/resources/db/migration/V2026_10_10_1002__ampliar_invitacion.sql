-- RN-USR-03: revocar invitaciones, trazar la cuenta creada y evitar duplicados abiertos
ALTER TABLE invitacion
  ADD COLUMN revocada_en TIMESTAMPTZ,
  ADD COLUMN revocada_por UUID REFERENCES usuario,
  ADD COLUMN usuario_id UUID REFERENCES usuario;

ALTER TABLE invitacion
  ADD CONSTRAINT invitacion_estado_ck
    CHECK (aceptada_en IS NULL OR revocada_en IS NULL),
  ADD CONSTRAINT invitacion_revocacion_ck
    CHECK ((revocada_en IS NULL) = (revocada_por IS NULL)),
  ADD CONSTRAINT invitacion_aceptacion_ck
    CHECK ((aceptada_en IS NULL) = (usuario_id IS NULL)) NOT VALID,
  ADD CONSTRAINT invitacion_usuario_uk UNIQUE (usuario_id);

-- Una invitación abierta (ni aceptada ni revocada) por correo. La expiración no entra:
-- depende de now() y un índice parcial exige predicados inmutables
CREATE UNIQUE INDEX invitacion_abierta_uk ON invitacion (lower(correo))
  WHERE aceptada_en IS NULL AND revocada_en IS NULL;

COMMENT ON COLUMN invitacion.revocada_en IS 'Cuándo un Administrador la anuló; NULL si no se anuló';
COMMENT ON COLUMN invitacion.revocada_por IS 'Administrador que la anuló';
COMMENT ON COLUMN invitacion.usuario_id IS 'Cuenta creada al aceptarla; NULL mientras esté pendiente o revocada';
