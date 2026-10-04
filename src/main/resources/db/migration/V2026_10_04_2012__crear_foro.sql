-- RN-FOR-01 a RN-FOR-09
CREATE TABLE hilo (
  id UUID PRIMARY KEY,
  puente_id UUID NOT NULL REFERENCES puente,
  inspeccion_id UUID UNIQUE REFERENCES inspeccion,
  autor_id UUID REFERENCES usuario,
  titulo VARCHAR(200) NOT NULL,
  categoria VARCHAR(25) NOT NULL
    CHECK (categoria IN ('DISCUSION_TECNICA', 'PROPUESTA_INTERVENCION', 'CONSULTA', 'REFERENCIA_DOCUMENTAL', 'ALERTA')),
  cerrado_en TIMESTAMPTZ,
  motivo_cierre TEXT,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- RN-FOR-08
  CHECK ((cerrado_en IS NULL) = (motivo_cierre IS NULL))
);

CREATE INDEX hilo_puente_idx ON hilo (puente_id);

CREATE TABLE comentario (
  id UUID PRIMARY KEY,
  hilo_id UUID NOT NULL REFERENCES hilo,
  autor_id UUID NOT NULL REFERENCES usuario,
  respuesta_a_id UUID REFERENCES comentario,
  elemento_ref VARCHAR(200),
  texto TEXT NOT NULL,
  editado_en TIMESTAMPTZ,
  eliminado_en TIMESTAMPTZ,
  oculto_en TIMESTAMPTZ,
  motivo_ocultamiento TEXT,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (respuesta_a_id <> id),
  -- RN-FOR-08
  CHECK ((oculto_en IS NULL) = (motivo_ocultamiento IS NULL))
);

CREATE INDEX comentario_hilo_idx ON comentario (hilo_id);

COMMENT ON TABLE hilo IS 'Hilo del foro permanente de un puente (RN-FOR-01)';
COMMENT ON COLUMN hilo.id IS 'UUID v7';
COMMENT ON COLUMN hilo.puente_id IS 'Puente del foro';
COMMENT ON COLUMN hilo.inspeccion_id IS 'Inspección publicada cuyo hilo público es este (RN-FOR-03)';
COMMENT ON COLUMN hilo.autor_id IS 'Quién lo abrió; NULL si lo abrió el Sistema al publicar';
COMMENT ON COLUMN hilo.titulo IS 'Título';
COMMENT ON COLUMN hilo.categoria IS 'Una de las cinco categorías (RN-FOR-06)';
COMMENT ON COLUMN hilo.cerrado_en IS 'Cuándo se cerró por moderación';
COMMENT ON COLUMN hilo.motivo_cierre IS 'Motivo obligatorio del cierre (RN-FOR-08)';
COMMENT ON COLUMN hilo.creado_en IS 'Fecha de creación, en UTC';
COMMENT ON COLUMN hilo.actualizado_en IS 'Última modificación, en UTC';

COMMENT ON TABLE comentario IS 'Comentario de un hilo; un solo nivel de respuesta (RN-FOR-04)';
COMMENT ON COLUMN comentario.id IS 'UUID v7';
COMMENT ON COLUMN comentario.hilo_id IS 'Hilo';
COMMENT ON COLUMN comentario.autor_id IS 'Autor';
COMMENT ON COLUMN comentario.respuesta_a_id IS 'Comentario al que responde; ese no puede ser a su vez una respuesta';
COMMENT ON COLUMN comentario.elemento_ref IS 'Campo de la inspección al que se ancla (RN-FOR-02)';
COMMENT ON COLUMN comentario.texto IS 'Texto; editable 15 minutos (RN-FOR-05)';
COMMENT ON COLUMN comentario.editado_en IS 'Última edición';
COMMENT ON COLUMN comentario.eliminado_en IS 'Lo borró su autor; se muestra la marca "comentario eliminado" (RN-FOR-05)';
COMMENT ON COLUMN comentario.oculto_en IS 'Lo ocultó la moderación (RN-FOR-08)';
COMMENT ON COLUMN comentario.motivo_ocultamiento IS 'Motivo obligatorio del ocultamiento';
COMMENT ON COLUMN comentario.creado_en IS 'Fecha de publicación, en UTC';
COMMENT ON COLUMN comentario.actualizado_en IS 'Última modificación, en UTC';
