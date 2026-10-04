-- RN-ARC-01 a RN-ARC-06, DT-BE-13: aquí solo va la referencia; el binario vive en MinIO
CREATE TABLE archivo (
  id UUID PRIMARY KEY,
  inspeccion_id UUID REFERENCES inspeccion,
  orden_mantenimiento_id UUID REFERENCES orden_mantenimiento,
  tipo VARCHAR(4) NOT NULL CHECK (tipo IN ('JPEG', 'WEBP', 'PDF')),
  clave VARCHAR(300) NOT NULL UNIQUE,
  clave_miniatura VARCHAR(300) UNIQUE,
  tamano BIGINT NOT NULL CHECK (tamano > 0),
  elemento_ref VARCHAR(200),
  ubicacion GEOGRAPHY(POINT, 4326),
  capturada_en TIMESTAMPTZ,
  purgado_en TIMESTAMPTZ,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- de una inspección o evidencia de una orden (RN-MTO-05), nunca de ambas
  CHECK (num_nonnulls(inspeccion_id, orden_mantenimiento_id) = 1),
  -- DT-ALM-04: toda foto tiene miniatura; un PDF no
  CHECK ((tipo = 'PDF') = (clave_miniatura IS NULL)),
  -- RN-ARC-04
  CHECK (tipo <> 'PDF' OR tamano <= 20971520)
);

CREATE INDEX archivo_inspeccion_idx ON archivo (inspeccion_id);
CREATE INDEX archivo_orden_mantenimiento_idx ON archivo (orden_mantenimiento_id);

COMMENT ON TABLE archivo IS 'Fotos y documentos; el contenido está en MinIO (DT-BE-13, DT-ALM-01)';
COMMENT ON COLUMN archivo.id IS 'UUID v7 generado en el cliente (DT-OFF-05)';
COMMENT ON COLUMN archivo.inspeccion_id IS 'Inspección a la que pertenece (RN-ARC-01)';
COMMENT ON COLUMN archivo.orden_mantenimiento_id IS 'Orden de la que es evidencia (RN-MTO-05)';
COMMENT ON COLUMN archivo.tipo IS 'Detectado por firma binaria, no por la extensión (DT-SEC-08)';
COMMENT ON COLUMN archivo.clave IS 'Clave del objeto: {año}/{mes}/{puente_id}/{inspeccion_id}/{uuid}.{ext} (DT-ALM-02)';
COMMENT ON COLUMN archivo.clave_miniatura IS 'Clave de la miniatura de 300 px (DT-ALM-04, ADR 0009)';
COMMENT ON COLUMN archivo.tamano IS 'Tamaño en bytes; un PDF hasta 20 MB (RN-ARC-04)';
COMMENT ON COLUMN archivo.elemento_ref IS 'Elemento del formulario al que se ancla la foto (RN-ARC-01)';
COMMENT ON COLUMN archivo.ubicacion IS 'Dónde se tomó (RN-ARC-03)';
COMMENT ON COLUMN archivo.capturada_en IS 'Cuándo se tomó (RN-ARC-03)';
COMMENT ON COLUMN archivo.purgado_en IS 'Cuándo se borró el objeto por la purga de 210 días; el registro queda (RN-ARC-06)';
COMMENT ON COLUMN archivo.creado_en IS 'Fecha de subida, en UTC';
COMMENT ON COLUMN archivo.actualizado_en IS 'Última modificación, en UTC';
