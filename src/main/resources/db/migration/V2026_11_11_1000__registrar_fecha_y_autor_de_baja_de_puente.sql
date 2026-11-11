-- RN-INV-08: la baja de un puente guarda cuándo y quién la hizo, para mostrarlo en la ficha.
-- Las bajas anteriores a esta migración quedan sin fecha ni autor registrados: no se inventan.
ALTER TABLE puente ADD COLUMN baja_en TIMESTAMPTZ;
ALTER TABLE puente ADD COLUMN baja_por UUID REFERENCES usuario (id);

-- Un puente activo no conserva fecha ni autor de baja (la reactivación los limpia)
ALTER TABLE puente
  ADD CONSTRAINT puente_baja_activo_ck
    CHECK (NOT activo OR (baja_en IS NULL AND baja_por IS NULL));

COMMENT ON COLUMN puente.baja_en IS 'Cuándo se dio de baja el puente (RN-INV-08); NULL en bajas anteriores a esta columna';
COMMENT ON COLUMN puente.baja_por IS 'Administrador que dio de baja el puente (RN-INV-08)';
