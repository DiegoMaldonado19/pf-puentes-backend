ALTER TABLE idempotencia
  ADD COLUMN huella_version INTEGER,
  ADD COLUMN huella_sha256 BYTEA,
  ADD COLUMN respuesta_bytes BYTEA;

ALTER TABLE idempotencia
  ADD CONSTRAINT idempotencia_resultado_exacto_ck
  CHECK (
    (huella_version IS NULL AND huella_sha256 IS NULL AND respuesta_bytes IS NULL)
    OR
    (huella_version IS NOT NULL
      AND huella_version >= 1
      AND huella_sha256 IS NOT NULL
      AND octet_length(huella_sha256) = 32
      AND respuesta_bytes IS NOT NULL)
  );

COMMENT ON COLUMN idempotencia.huella_version IS
  'Versión explícita del formato de huella aportada por el integrador; NULL para filas heredadas';
COMMENT ON COLUMN idempotencia.huella_sha256 IS
  'Huella SHA-256 de 32 bytes aportada por el integrador; NULL para filas heredadas';
COMMENT ON COLUMN idempotencia.respuesta_bytes IS
  'Cuerpo binario exacto de la respuesta admitida; NULL para filas heredadas';
