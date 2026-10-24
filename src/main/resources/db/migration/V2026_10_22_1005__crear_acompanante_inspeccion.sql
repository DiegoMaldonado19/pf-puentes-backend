-- RN-INS-02: acompañantes de una inspección, sin responsabilidad sobre el registro
CREATE TABLE acompanante_inspeccion (
  id UUID PRIMARY KEY,
  inspeccion_id UUID NOT NULL REFERENCES inspeccion,
  nombre VARCHAR(150) NOT NULL CHECK (btrim(nombre) <> ''),
  usuario_id UUID REFERENCES usuario,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- Una cuenta no se repite como acompañante; varios sin cuenta sí (NULL no choca consigo mismo)
  UNIQUE (inspeccion_id, usuario_id)
);

COMMENT ON TABLE acompanante_inspeccion IS 'Personas que acompañaron al autor en la visita; el autor sigue siendo el único responsable (RN-INS-02)';
COMMENT ON COLUMN acompanante_inspeccion.id IS 'UUID v7';
COMMENT ON COLUMN acompanante_inspeccion.inspeccion_id IS 'Inspección en la que participó';
COMMENT ON COLUMN acompanante_inspeccion.nombre IS 'Nombre del acompañante, con o sin cuenta en el sistema';
COMMENT ON COLUMN acompanante_inspeccion.usuario_id IS 'Cuenta del acompañante si la tiene; NULL si es una persona externa';
COMMENT ON COLUMN acompanante_inspeccion.creado_en IS 'Fecha de registro, en UTC';
COMMENT ON COLUMN acompanante_inspeccion.actualizado_en IS 'Última modificación, en UTC';
