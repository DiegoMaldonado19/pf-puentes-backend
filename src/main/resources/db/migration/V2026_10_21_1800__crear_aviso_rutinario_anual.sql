-- RN-MTO-07, DT-BE-14: un aviso por puente, destinatario y fecha base del período anual.
CREATE TABLE aviso_rutinario_anual (
  id UUID PRIMARY KEY,
  puente_id UUID NOT NULL REFERENCES puente,
  usuario_id UUID NOT NULL REFERENCES usuario,
  fecha_referencia DATE NOT NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (puente_id, usuario_id, fecha_referencia)
);

COMMENT ON TABLE aviso_rutinario_anual IS 'Marca de recordatorio rutinario anual enviada atómicamente con P9, una por destinatario y período';
COMMENT ON COLUMN aviso_rutinario_anual.id IS 'UUID v7';
COMMENT ON COLUMN aviso_rutinario_anual.puente_id IS 'Puente sin rutina programada dentro del plazo anual';
COMMENT ON COLUMN aviso_rutinario_anual.usuario_id IS 'Catedrático o Administrador activo destinatario del recordatorio';
COMMENT ON COLUMN aviso_rutinario_anual.fecha_referencia IS 'Fecha de última ejecución rutinaria o registro inicial sin ejecuciones, origen del período';
COMMENT ON COLUMN aviso_rutinario_anual.creado_en IS 'Fecha UTC del recordatorio confirmado';
COMMENT ON COLUMN aviso_rutinario_anual.actualizado_en IS 'Marca UTC de la fila interna';
