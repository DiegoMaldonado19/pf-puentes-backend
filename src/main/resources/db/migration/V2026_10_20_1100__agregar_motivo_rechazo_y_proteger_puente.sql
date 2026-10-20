-- RN-INV-07: el rechazo de una solicitud de alta lleva un motivo escrito
ALTER TABLE solicitud_puente ADD COLUMN motivo_rechazo VARCHAR(500);

ALTER TABLE solicitud_puente
  ADD CONSTRAINT solicitud_puente_motivo_rechazo_ck
    CHECK (estado <> 'RECHAZADA' OR btrim(coalesce(motivo_rechazo, '')) <> '');

-- RN-INV-08: la baja de un puente lleva un motivo que no puede ser solo espacios
ALTER TABLE puente
  ADD CONSTRAINT puente_motivo_baja_ck CHECK (motivo_baja IS NULL OR btrim(motivo_baja) <> '');

COMMENT ON COLUMN solicitud_puente.motivo_rechazo IS 'Motivo escrito por el Administrador al rechazar; obligatorio si está RECHAZADA (RN-INV-07)';

-- RN-INV-08, DT-BD-11: los puentes nunca se borran físicamente, solo se dan de baja
DO $$
BEGIN
  -- Igual que V2026_10_10_1005: el rol solo existe en los ambientes con docker/postgres/01-usuario-app.sh
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'puentes_app') THEN
    REVOKE DELETE ON puente FROM puentes_app;
  END IF;
END
$$;
