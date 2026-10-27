ALTER TABLE intento_documento
    ADD COLUMN tipo_mime varchar(100) NOT NULL DEFAULT 'application/pdf',
    ADD COLUMN clave_miniatura text,
    ADD COLUMN miniatura_sha256 varchar(64),
    ADD COLUMN miniatura_tamano bigint,
    ADD COLUMN miniatura_mime varchar(100),
    ADD CONSTRAINT intento_miniatura_completa CHECK (
        (clave_miniatura IS NULL AND miniatura_sha256 IS NULL AND miniatura_tamano IS NULL AND miniatura_mime IS NULL)
        OR (clave_miniatura IS NOT NULL AND miniatura_sha256 IS NOT NULL AND miniatura_tamano IS NOT NULL AND miniatura_tamano >= 0 AND miniatura_mime IS NOT NULL));

COMMENT ON TABLE intento_documento IS 'Intención durable de carga PDF o foto/miniatura; sobrevive al rollback y no expira automáticamente.';
COMMENT ON COLUMN intento_documento.tipo_mime IS 'MIME detectado esperado del objeto principal; PDF para intenciones previas.';
COMMENT ON COLUMN intento_documento.clave_miniatura IS 'Identidad MinIO estable de la miniatura; nula en PDF.';
COMMENT ON COLUMN intento_documento.miniatura_sha256 IS 'SHA-256 hexadecimal esperado de la miniatura.';
COMMENT ON COLUMN intento_documento.miniatura_tamano IS 'Longitud esperada de la miniatura en bytes.';
COMMENT ON COLUMN intento_documento.miniatura_mime IS 'MIME detectado esperado de la miniatura.';
