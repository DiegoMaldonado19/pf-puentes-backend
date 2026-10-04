-- RN-IC-03: pesos por elemento, versionados junto con el formulario
CREATE TABLE version_pesos (
  id UUID PRIMARY KEY,
  formulario_version_id UUID NOT NULL REFERENCES formulario_version,
  acta VARCHAR(300),
  vigente_desde TIMESTAMPTZ NOT NULL DEFAULT now(),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE peso_elemento (
  id UUID PRIMARY KEY,
  version_pesos_id UUID NOT NULL REFERENCES version_pesos,
  elemento_ref VARCHAR(200) NOT NULL,
  peso NUMERIC(6, 3) NOT NULL CHECK (peso > 0),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (version_pesos_id, elemento_ref)
);

COMMENT ON TABLE version_pesos IS 'Juego de pesos del IC para una versión del formulario (RN-IC-03)';
COMMENT ON COLUMN version_pesos.id IS 'UUID v7';
COMMENT ON COLUMN version_pesos.formulario_version_id IS 'Versión del formulario a la que aplica';
COMMENT ON COLUMN version_pesos.acta IS 'Referencia al acta de calibración firmada; NULL = pesos provisionales sin acta (pendiente #11)';
COMMENT ON COLUMN version_pesos.vigente_desde IS 'Desde cuándo se usa para calcular el IC';
COMMENT ON COLUMN version_pesos.creado_en IS 'Fecha de creación, en UTC';
COMMENT ON COLUMN version_pesos.actualizado_en IS 'Última modificación, en UTC';

COMMENT ON TABLE peso_elemento IS 'Peso de cada elemento en la fórmula del IC (RN-IC-02)';
COMMENT ON COLUMN peso_elemento.id IS 'UUID v7';
COMMENT ON COLUMN peso_elemento.version_pesos_id IS 'Juego de pesos al que pertenece';
COMMENT ON COLUMN peso_elemento.elemento_ref IS 'Ruta del elemento en el formulario (contrato 2)';
COMMENT ON COLUMN peso_elemento.peso IS 'Criticidad estructural del elemento';
COMMENT ON COLUMN peso_elemento.creado_en IS 'Fecha de creación, en UTC';
COMMENT ON COLUMN peso_elemento.actualizado_en IS 'Última modificación, en UTC';
