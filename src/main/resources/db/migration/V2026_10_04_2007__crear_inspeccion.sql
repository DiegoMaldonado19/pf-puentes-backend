-- RN-INS-01 a RN-INS-15, RN-IC-01 a RN-IC-07, DT-BD-02
CREATE TABLE inspeccion (
  id UUID PRIMARY KEY,
  puente_id UUID NOT NULL REFERENCES puente,
  autor_id UUID NOT NULL REFERENCES usuario,
  formulario_version_id UUID NOT NULL REFERENCES formulario_version,
  supersede_a_id UUID REFERENCES inspeccion,
  estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR'
    CHECK (estado IN ('BORRADOR', 'ENVIADA', 'EN_REVISION', 'CAMBIOS_SOLICITADOS', 'PUBLICADA', 'RECHAZADA')),
  fecha_inspeccion DATE NOT NULL,
  datos JSONB NOT NULL DEFAULT '{}',
  ubicacion_inicio GEOGRAPHY(POINT, 4326),
  dispositivo_id VARCHAR(100),
  enviada_en TIMESTAMPTZ,
  sincronizada_en TIMESTAMPTZ,
  publicada_en TIMESTAMPTZ,
  version_pesos_id UUID REFERENCES version_pesos,
  indice_condicion NUMERIC(5, 2) CHECK (indice_condicion BETWEEN 0 AND 100),
  estado_calculado VARCHAR(10) CHECK (estado_calculado IN ('BUENO', 'REGULAR', 'MALO')),
  anulacion VARCHAR(20)
    CHECK (anulacion IN ('SOCAVACION', 'ASENTAMIENTO', 'PERDIDA_SECCION', 'AUSENCIA_PORTANTE')),
  estado_confirmado VARCHAR(10) CHECK (estado_confirmado IN ('BUENO', 'REGULAR', 'MALO')),
  justificacion_estado TEXT,
  eliminado_en TIMESTAMPTZ,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (supersede_a_id <> id),
  -- RN-IC-07: el IC solo existe en las publicadas, y se guarda al publicar (contrato 3)
  CHECK ((estado = 'PUBLICADA') = (indice_condicion IS NOT NULL AND estado_calculado IS NOT NULL
    AND estado_confirmado IS NOT NULL AND publicada_en IS NOT NULL)),
  -- RN-IC-06: cambiar el estado calculado exige justificación
  CHECK (estado_confirmado IS NULL OR estado_confirmado = estado_calculado OR justificacion_estado IS NOT NULL),
  -- RN-INS-07: una publicada no se elimina
  CHECK (estado <> 'PUBLICADA' OR eliminado_en IS NULL)
);

CREATE INDEX inspeccion_puente_idx ON inspeccion (puente_id);
CREATE INDEX inspeccion_autor_idx ON inspeccion (autor_id);
-- DT-BD-03
CREATE INDEX inspeccion_datos_idx ON inspeccion USING GIN (datos);

COMMENT ON TABLE inspeccion IS 'Inspección de un puente: columnas tipadas + daños en JSONB (DT-BD-02)';
COMMENT ON COLUMN inspeccion.id IS 'UUID v7 generado en el cliente, antes de cualquier contacto con el servidor (DT-OFF-05)';
COMMENT ON COLUMN inspeccion.puente_id IS 'Puente inspeccionado';
COMMENT ON COLUMN inspeccion.autor_id IS 'Autor único y responsable (RN-INS-02)';
COMMENT ON COLUMN inspeccion.formulario_version_id IS 'Versión del formulario con la que se levantó (RN-FRM-02)';
COMMENT ON COLUMN inspeccion.supersede_a_id IS 'Inspección publicada que esta corrige (RN-INS-08)';
COMMENT ON COLUMN inspeccion.estado IS 'Estado de la máquina de RN-INS-04';
COMMENT ON COLUMN inspeccion.fecha_inspeccion IS 'Ni futura ni anterior al año de construcción del puente (RN-INS-03)';
COMMENT ON COLUMN inspeccion.datos IS 'Respuestas del formulario, validadas contra el JSON Schema de su versión (RN-FRM-08)';
COMMENT ON COLUMN inspeccion.ubicacion_inicio IS 'GPS del dispositivo al iniciar; se compara con el puente (RN-INS-12)';
COMMENT ON COLUMN inspeccion.dispositivo_id IS 'Identificador del dispositivo (RN-INS-13)';
COMMENT ON COLUMN inspeccion.enviada_en IS 'Fecha y hora de envío a revisión (RN-INS-13)';
COMMENT ON COLUMN inspeccion.sincronizada_en IS 'Fecha y hora de sincronización (RN-INS-13)';
COMMENT ON COLUMN inspeccion.publicada_en IS 'Fecha y hora de publicación';
COMMENT ON COLUMN inspeccion.version_pesos_id IS 'Pesos con los que se calculó el IC (RN-IC-08)';
COMMENT ON COLUMN inspeccion.indice_condicion IS 'IC de 0 a 100, calculado al publicar (RN-IC-01, RN-IC-02)';
COMMENT ON COLUMN inspeccion.estado_calculado IS 'Estado que sale del IC y de las anulaciones (RN-IC-04, RN-IC-05)';
COMMENT ON COLUMN inspeccion.anulacion IS 'Condición de RN-IC-05 que forzó Malo; NULL si ninguna';
COMMENT ON COLUMN inspeccion.estado_confirmado IS 'Estado que confirma el evaluador; es el que toma el puente (RN-IC-06, pendiente #12)';
COMMENT ON COLUMN inspeccion.justificacion_estado IS 'Obligatoria si el estado confirmado difiere del calculado';
COMMENT ON COLUMN inspeccion.eliminado_en IS 'Borrado lógico de un borrador (RN-INS-15, pendiente #6)';
COMMENT ON COLUMN inspeccion.creado_en IS 'Fecha y hora de creación (RN-INS-13), en UTC';
COMMENT ON COLUMN inspeccion.actualizado_en IS 'Última modificación, en UTC';
