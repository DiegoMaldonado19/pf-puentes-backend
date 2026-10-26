-- RN-INS-01, RN-INS-05, DT-BE-08: "mis inspecciones" y "inspecciones de un puente", ordenadas y paginadas
-- Cada índice nuevo empieza por la columna del que reemplaza, así que no se pierde ninguna consulta
DROP INDEX inspeccion_autor_idx;
DROP INDEX inspeccion_puente_idx;

CREATE INDEX inspeccion_autor_fecha_idx ON inspeccion (autor_id, creado_en DESC);
CREATE INDEX inspeccion_puente_fecha_idx ON inspeccion (puente_id, fecha_inspeccion DESC, creado_en DESC);
