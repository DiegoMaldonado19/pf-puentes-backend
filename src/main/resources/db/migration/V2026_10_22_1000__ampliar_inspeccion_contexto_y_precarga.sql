-- RN-INS-05, RN-INS-14, RN-REV-01, DT-SEC-04 (curso de origen) y RN-INS-09 (datos permanentes)
ALTER TABLE inspeccion
  ADD COLUMN curso_id UUID REFERENCES curso,
  ADD COLUMN precargada_de_id UUID,
  ADD COLUMN datos_permanentes_confirmados_en TIMESTAMPTZ;

-- Destino de las llaves compuestas: fuerza que dos inspecciones ligadas sean del mismo puente
ALTER TABLE inspeccion
  ADD CONSTRAINT inspeccion_id_puente_uk UNIQUE (id, puente_id);

ALTER TABLE inspeccion
  ADD CONSTRAINT inspeccion_precarga_mismo_puente_fk
    FOREIGN KEY (precargada_de_id, puente_id) REFERENCES inspeccion (id, puente_id),
  ADD CONSTRAINT inspeccion_precarga_ck
    CHECK (precargada_de_id IS NULL OR precargada_de_id <> id);

-- Bandeja del catedrático y autorización por fila: inspecciones de un curso por estado
CREATE INDEX inspeccion_curso_estado_idx ON inspeccion (curso_id, estado)
  WHERE curso_id IS NOT NULL;

COMMENT ON COLUMN inspeccion.curso_id IS 'Curso en cuyo contexto se levantó; su Catedrático es el "asignado" (RN-INS-05). NULL en las de Catedrático y Profesional externo';
COMMENT ON COLUMN inspeccion.precargada_de_id IS 'Inspección publicada del mismo puente de la que se precargaron los datos permanentes (RN-INS-09); NULL si no había ninguna';
COMMENT ON COLUMN inspeccion.datos_permanentes_confirmados_en IS 'Cuándo el autor confirmó explícitamente los datos permanentes (RN-INS-09); NULL mientras no los confirme';
