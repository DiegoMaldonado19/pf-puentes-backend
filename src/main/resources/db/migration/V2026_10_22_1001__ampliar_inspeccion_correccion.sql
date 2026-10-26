-- RN-INS-08: la corrección explica qué enmienda, es del mismo puente y no se duplica
ALTER TABLE inspeccion
  ADD COLUMN motivo_correccion VARCHAR(500);

ALTER TABLE inspeccion
  -- NOT VALID: rige para filas nuevas o modificadas; no escanea ni falla por datos previos
  ADD CONSTRAINT inspeccion_correccion_motivo_ck
    CHECK ((supersede_a_id IS NULL) = (motivo_correccion IS NULL)) NOT VALID,
  ADD CONSTRAINT inspeccion_correccion_motivo_texto_ck
    CHECK (motivo_correccion IS NULL OR btrim(motivo_correccion) <> ''),
  ADD CONSTRAINT inspeccion_correccion_mismo_puente_fk
    FOREIGN KEY (supersede_a_id, puente_id) REFERENCES inspeccion (id, puente_id);

-- Una sola corrección viva por inspección corregida. Las rechazadas y las eliminadas no cuentan
CREATE UNIQUE INDEX inspeccion_correccion_viva_uk ON inspeccion (supersede_a_id)
  WHERE supersede_a_id IS NOT NULL AND eliminado_en IS NULL AND estado <> 'RECHAZADA';

COMMENT ON COLUMN inspeccion.motivo_correccion IS 'Error que enmienda esta corrección; obligatorio si supersede_a_id tiene valor (RN-INS-08)';
