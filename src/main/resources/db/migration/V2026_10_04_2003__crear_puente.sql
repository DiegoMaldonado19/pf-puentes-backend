-- RN-INV-01 a RN-INV-10, DT-BD-04
CREATE TABLE puente (
  id UUID PRIMARY KEY,
  codigo VARCHAR(20) NOT NULL UNIQUE CHECK (codigo ~ '^GT-[0-9]{2}-[0-9]{2,4}-[0-9]{4}$'),
  nombre VARCHAR(200) NOT NULL,
  municipio_codigo VARCHAR(4) NOT NULL REFERENCES municipio,
  ruta VARCHAR(100) NOT NULL,
  ubicacion GEOGRAPHY(POINT, 4326) NOT NULL,
  anio_construccion INTEGER CHECK (anio_construccion BETWEEN 1800 AND 2100),
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  motivo_baja TEXT,
  estado_actual VARCHAR(10) CHECK (estado_actual IN ('BUENO', 'REGULAR', 'MALO')),
  indice_condicion_actual NUMERIC(5, 2) CHECK (indice_condicion_actual BETWEEN 0 AND 100),
  fecha_ultima_inspeccion DATE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- RN-INV-08
  CHECK (activo OR motivo_baja IS NOT NULL),
  -- RN-INV-09: los tres salen juntos de la última inspección publicada; sin ella, "Sin evaluar"
  CHECK (num_nulls(estado_actual, indice_condicion_actual, fecha_ultima_inspeccion) IN (0, 3))
);

-- DT-BD-04: ST_DWithin para duplicados a 100 m (RN-INV-06) y para el GPS a 500 m (RN-INS-12)
CREATE INDEX puente_ubicacion_idx ON puente USING GIST (ubicacion);

CREATE TABLE solicitud_puente (
  id UUID PRIMARY KEY,
  catedratico_id UUID NOT NULL REFERENCES usuario,
  nombre VARCHAR(200) NOT NULL,
  municipio_codigo VARCHAR(4) NOT NULL REFERENCES municipio,
  ruta VARCHAR(100) NOT NULL,
  ubicacion GEOGRAPHY(POINT, 4326) NOT NULL,
  estado VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
  administrador_id UUID REFERENCES usuario,
  puente_id UUID REFERENCES puente,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (estado <> 'APROBADA' OR puente_id IS NOT NULL),
  CHECK (estado = 'PENDIENTE' OR administrador_id IS NOT NULL)
);

COMMENT ON TABLE puente IS 'Inventario permanente: un registro por puente, nunca por visita (RN-INV-01)';
COMMENT ON COLUMN puente.id IS 'UUID v7';
COMMENT ON COLUMN puente.codigo IS 'GT-<DEPTO>-<MUNI>-<CORRELATIVO>, único e inmutable (RN-INV-02, RN-INV-03)';
COMMENT ON COLUMN puente.nombre IS 'Nombre del puente';
COMMENT ON COLUMN puente.municipio_codigo IS 'Municipio INE; el departamento sale del municipio';
COMMENT ON COLUMN puente.ruta IS 'Ruta o carretera';
COMMENT ON COLUMN puente.ubicacion IS 'Punto WGS84 (EPSG:4326); la UTM se calcula al mostrar (RN-INV-05)';
COMMENT ON COLUMN puente.anio_construccion IS 'Año de construcción; la inspección no puede ser anterior (RN-INS-03)';
COMMENT ON COLUMN puente.activo IS 'FALSE = baja lógica (RN-INV-08)';
COMMENT ON COLUMN puente.motivo_baja IS 'Motivo de la baja: demolido, sustituido, duplicado…';
COMMENT ON COLUMN puente.estado_actual IS 'Derivado de la última inspección publicada; NULL = Sin evaluar (RN-INV-10)';
COMMENT ON COLUMN puente.indice_condicion_actual IS 'IC de la última inspección publicada (RN-INV-09)';
COMMENT ON COLUMN puente.fecha_ultima_inspeccion IS 'Fecha de la última inspección publicada (RN-INV-09)';
COMMENT ON COLUMN puente.creado_en IS 'Fecha de alta, en UTC';
COMMENT ON COLUMN puente.actualizado_en IS 'Última modificación, en UTC';

COMMENT ON TABLE solicitud_puente IS 'Solicitudes de alta de un Catedrático, pendientes de un Administrador (RN-INV-07)';
COMMENT ON COLUMN solicitud_puente.id IS 'UUID v7';
COMMENT ON COLUMN solicitud_puente.catedratico_id IS 'Quién solicita';
COMMENT ON COLUMN solicitud_puente.nombre IS 'Nombre propuesto';
COMMENT ON COLUMN solicitud_puente.municipio_codigo IS 'Municipio INE propuesto';
COMMENT ON COLUMN solicitud_puente.ruta IS 'Ruta propuesta';
COMMENT ON COLUMN solicitud_puente.ubicacion IS 'Punto WGS84 propuesto';
COMMENT ON COLUMN solicitud_puente.estado IS 'PENDIENTE, APROBADA o RECHAZADA';
COMMENT ON COLUMN solicitud_puente.administrador_id IS 'Administrador que la resolvió';
COMMENT ON COLUMN solicitud_puente.puente_id IS 'Puente creado al aprobarla';
COMMENT ON COLUMN solicitud_puente.creado_en IS 'Fecha de la solicitud, en UTC';
COMMENT ON COLUMN solicitud_puente.actualizado_en IS 'Última modificación, en UTC';
