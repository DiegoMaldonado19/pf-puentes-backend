-- RN-USR-08 (baja lógica con rastro), RN-USR-04 (quién verificó), RN-USR-01 (garantía en BD)
ALTER TABLE usuario
  ADD COLUMN desactivado_en TIMESTAMPTZ,
  ADD COLUMN desactivado_por UUID REFERENCES usuario,
  ADD COLUMN motivo_baja VARCHAR(500),
  ADD COLUMN colegiado_verificado_por UUID REFERENCES usuario;

-- Cualquier cuenta que ya esté INACTIVO recibe datos de baja para cumplir la restricción
UPDATE usuario
SET desactivado_en = actualizado_en,
    motivo_baja = 'Baja anterior al registro de motivos'
WHERE estado = 'INACTIVO';

ALTER TABLE usuario
  ADD CONSTRAINT usuario_baja_ck
    CHECK ((estado = 'INACTIVO') = (desactivado_en IS NOT NULL)),
  ADD CONSTRAINT usuario_baja_motivo_ck
    CHECK ((desactivado_en IS NULL) = (motivo_baja IS NULL)),
  ADD CONSTRAINT usuario_baja_motivo_texto_ck
    CHECK (motivo_baja IS NULL OR btrim(motivo_baja) <> ''),
  ADD CONSTRAINT usuario_baja_responsable_ck
    CHECK (desactivado_por IS NULL OR desactivado_en IS NOT NULL),
  -- NOT VALID: rige para filas nuevas o modificadas; no escanea ni falla por datos previos
  ADD CONSTRAINT usuario_colegiado_verificador_ck
    CHECK ((colegiado_verificado_en IS NULL) = (colegiado_verificado_por IS NULL)) NOT VALID,
  ADD CONSTRAINT usuario_activo_correo_ck
    CHECK (estado <> 'ACTIVO' OR correo_verificado_en IS NOT NULL) NOT VALID;

-- D4: un colegiado por cuenta viva. Se ignoran mayúsculas y espacios; las bajas no bloquean el número
CREATE UNIQUE INDEX usuario_colegiado_uk ON usuario (upper(btrim(numero_colegiado)))
  WHERE numero_colegiado IS NOT NULL AND estado <> 'INACTIVO';

COMMENT ON COLUMN usuario.desactivado_en IS 'Cuándo se dio de baja la cuenta; NULL mientras no esté INACTIVO (RN-USR-08)';
COMMENT ON COLUMN usuario.desactivado_por IS 'Administrador que dio la baja; NULL si no hay baja o la hizo el Sistema';
COMMENT ON COLUMN usuario.motivo_baja IS 'Motivo escrito de la baja; también el del rechazo de un colegiado';
COMMENT ON COLUMN usuario.colegiado_verificado_por IS 'Administrador que verificó el número de colegiado (RN-USR-04)';
