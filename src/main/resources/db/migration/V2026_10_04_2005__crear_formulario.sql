-- RN-FRM-01 a RN-FRM-06
CREATE TABLE formulario_version (
  id UUID PRIMARY KEY,
  codigo VARCHAR(20) NOT NULL UNIQUE,
  esquema JSONB NOT NULL,
  mapeo JSONB,
  estado VARCHAR(10) NOT NULL DEFAULT 'BORRADOR' CHECK (estado IN ('BORRADOR', 'ACTIVA', 'RETIRADA')),
  publicada_en TIMESTAMPTZ,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (estado = 'BORRADOR' OR publicada_en IS NOT NULL)
);

-- RN-FRM-04: una sola versión activa a la vez
CREATE UNIQUE INDEX formulario_version_activa_uk ON formulario_version (estado) WHERE estado = 'ACTIVA';

COMMENT ON TABLE formulario_version IS 'Versiones del formulario SIECA como JSON Schema (RN-FRM-01); publicada, es inmutable (RN-FRM-03)';
COMMENT ON COLUMN formulario_version.id IS 'UUID v7';
COMMENT ON COLUMN formulario_version.codigo IS 'Código de la versión que guarda cada inspección (RN-FRM-02)';
COMMENT ON COLUMN formulario_version.esquema IS 'JSON Schema con los metadatos del motor (contrato 1)';
COMMENT ON COLUMN formulario_version.mapeo IS 'Mapeo de campos respecto de la versión anterior (RN-FRM-06); NULL en la primera';
COMMENT ON COLUMN formulario_version.estado IS 'BORRADOR mientras se edita; ACTIVA la que usan las inspecciones nuevas; RETIRADA solo para consultar';
COMMENT ON COLUMN formulario_version.publicada_en IS 'Cuándo se publicó';
COMMENT ON COLUMN formulario_version.creado_en IS 'Fecha de creación, en UTC';
COMMENT ON COLUMN formulario_version.actualizado_en IS 'Última modificación, en UTC';
