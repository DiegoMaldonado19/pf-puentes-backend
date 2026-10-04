-- RN-INV-03, RN-INV-04. Clave natural: el código oficial del INE (ADR 0011). Los datos los carga P3.
CREATE TABLE departamento (
  codigo VARCHAR(2) PRIMARY KEY CHECK (codigo ~ '^[0-9]{2}$'),
  nombre VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE municipio (
  codigo VARCHAR(4) PRIMARY KEY CHECK (codigo ~ '^[0-9]{4}$'),
  departamento_codigo VARCHAR(2) NOT NULL REFERENCES departamento,
  nombre VARCHAR(80) NOT NULL,
  UNIQUE (departamento_codigo, nombre),
  -- el código del INE empieza con el de su departamento: así no puede quedar incongruente
  CHECK (left(codigo, 2) = departamento_codigo)
);

COMMENT ON TABLE departamento IS 'Departamentos de Guatemala según el INE';
COMMENT ON COLUMN departamento.codigo IS 'Código INE de dos dígitos';
COMMENT ON COLUMN departamento.nombre IS 'Nombre oficial';

COMMENT ON TABLE municipio IS 'Municipios de Guatemala según el INE';
COMMENT ON COLUMN municipio.codigo IS 'Código INE de cuatro dígitos; los dos primeros son el departamento';
COMMENT ON COLUMN municipio.departamento_codigo IS 'Departamento al que pertenece';
COMMENT ON COLUMN municipio.nombre IS 'Nombre oficial';
