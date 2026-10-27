CREATE TABLE intento_baja_archivo (
 usuario_id uuid NOT NULL,
 clave varchar(100) NOT NULL,
 archivo_id uuid NOT NULL,
 inspeccion_id uuid NOT NULL,
 clave_objeto text NOT NULL,
 clave_miniatura text,
 huella_version integer NOT NULL,
 huella_sha256 bytea NOT NULL,
 finalizada boolean NOT NULL DEFAULT false,
 PRIMARY KEY (usuario_id, clave)
);
CREATE UNIQUE INDEX intento_baja_archivo_pendiente ON intento_baja_archivo (archivo_id) WHERE NOT finalizada;
COMMENT ON TABLE intento_baja_archivo IS 'Manifiesto durable de baja física y autorización por padre vigente, sin expiración automática';
COMMENT ON COLUMN intento_baja_archivo.usuario_id IS 'Usuario de la operación, no concede permisos';
COMMENT ON COLUMN intento_baja_archivo.clave IS 'Clave de operación recortada';
COMMENT ON COLUMN intento_baja_archivo.archivo_id IS 'UUID solicitado, reutilizable tras finalizar';
COMMENT ON COLUMN intento_baja_archivo.inspeccion_id IS 'Identidad del padre que debe autorizarse actualmente';
COMMENT ON COLUMN intento_baja_archivo.clave_objeto IS 'Clave física exacta del objeto principal';
COMMENT ON COLUMN intento_baja_archivo.clave_miniatura IS 'Clave física exacta de miniatura opcional';
COMMENT ON COLUMN intento_baja_archivo.huella_version IS 'Versión del contrato de huella';
COMMENT ON COLUMN intento_baja_archivo.huella_sha256 IS 'SHA-256 de método y recurso canónico';
COMMENT ON COLUMN intento_baja_archivo.finalizada IS 'Baja y resultado confirmados atómicamente';
