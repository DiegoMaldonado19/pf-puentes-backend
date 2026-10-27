CREATE TABLE intento_documento (
  usuario_id UUID NOT NULL,
  clave VARCHAR(100) NOT NULL,
  huella_version INTEGER NOT NULL CHECK (huella_version > 0),
  huella_sha256 BYTEA NOT NULL CHECK (octet_length(huella_sha256) = 32),
  archivo_id UUID NOT NULL,
  inspeccion_id UUID NOT NULL,
  clave_objeto TEXT NOT NULL UNIQUE,
  contenido_sha256 VARCHAR(64) NOT NULL CHECK (contenido_sha256 ~ '^[0-9a-f]{64}$'),
  tamano BIGINT NOT NULL CHECK (tamano >= 0),
  finalizada BOOLEAN NOT NULL DEFAULT FALSE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (usuario_id, clave)
);
CREATE UNIQUE INDEX intento_documento_pendiente_uk ON intento_documento (archivo_id) WHERE NOT finalizada;
COMMENT ON TABLE intento_documento IS 'Intención durable de carga PDF antes de MinIO; sin expiración ni purga automática. Identidades sin FK para no depender de filas aún no confirmadas o posteriormente eliminadas';
COMMENT ON COLUMN intento_documento.usuario_id IS 'Usuario que inicia la operación; no reemplaza su autorización vigente';
COMMENT ON COLUMN intento_documento.clave IS 'Clave de idempotencia normalizada del usuario';
COMMENT ON COLUMN intento_documento.huella_version IS 'Versión del formato de huella de la operación PDF';
COMMENT ON COLUMN intento_documento.huella_sha256 IS 'SHA-256 de método, recurso, UUID y manifiesto multipart';
COMMENT ON COLUMN intento_documento.archivo_id IS 'UUID del archivo proporcionado por el cliente';
COMMENT ON COLUMN intento_documento.inspeccion_id IS 'Identidad del padre; siempre se reautoriza contra la inspección vigente';
COMMENT ON COLUMN intento_documento.clave_objeto IS 'Clave S3 estable elegida y confirmada antes del primer efecto externo';
COMMENT ON COLUMN intento_documento.contenido_sha256 IS 'SHA-256 hexadecimal del contenido PDF esperado';
COMMENT ON COLUMN intento_documento.tamano IS 'Cantidad esperada de bytes PDF';
COMMENT ON COLUMN intento_documento.finalizada IS 'La fila de archivo y la actividad se confirmaron; actualización atómica con el resultado de la operación cuando lleva clave';
COMMENT ON COLUMN intento_documento.creado_en IS 'Momento de confirmación de la intención durable, no de admisión del resultado HTTP';
