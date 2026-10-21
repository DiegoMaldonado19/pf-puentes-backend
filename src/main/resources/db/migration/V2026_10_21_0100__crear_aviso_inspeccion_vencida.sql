-- RN-MTO-08, DT-BE-14: deduplicación por destinatario y última publicación (NULL = nunca publicada).
CREATE TABLE aviso_inspeccion_vencida (
  id UUID PRIMARY KEY,
  puente_id UUID NOT NULL REFERENCES puente,
  usuario_id UUID NOT NULL REFERENCES usuario,
  inspeccion_id UUID REFERENCES inspeccion,
  referencia_en TIMESTAMPTZ NOT NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE NULLS NOT DISTINCT (puente_id, usuario_id, inspeccion_id)
);

CREATE INDEX inspeccion_ultima_publicada_idx
  ON inspeccion (puente_id, publicada_en DESC, id DESC) WHERE estado = 'PUBLICADA';

COMMENT ON TABLE aviso_inspeccion_vencida IS 'Marca interna de notificación de inspección vencida, atómica con P9; no se borra al leer el aviso';
COMMENT ON COLUMN aviso_inspeccion_vencida.id IS 'UUID v7';
COMMENT ON COLUMN aviso_inspeccion_vencida.puente_id IS 'Puente objeto del aviso';
COMMENT ON COLUMN aviso_inspeccion_vencida.usuario_id IS 'Destinatario avisado: Catedrático o Administrador activo al evaluar';
COMMENT ON COLUMN aviso_inspeccion_vencida.inspeccion_id IS 'Última publicación del período; NULL si nunca tuvo una publicada';
COMMENT ON COLUMN aviso_inspeccion_vencida.referencia_en IS 'Fecha UTC de publicación o, si no hubo, registro del puente';
COMMENT ON COLUMN aviso_inspeccion_vencida.creado_en IS 'Fecha UTC del envío confirmado';
COMMENT ON COLUMN aviso_inspeccion_vencida.actualizado_en IS 'Marca UTC de la fila interna';
