-- RN-INS-12, RN-INS-13, RN-IC-06, RN-OFF-09 y DT-BD-10: captura en campo, sincronización y estado confirmado
ALTER TABLE inspeccion
  ADD COLUMN distancia_a_puente_m NUMERIC(10, 2),
  ADD COLUMN creada_en_dispositivo TIMESTAMPTZ,
  ADD COLUMN estado_confirmado_por UUID REFERENCES usuario,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE inspeccion
  ADD CONSTRAINT inspeccion_distancia_ck
    CHECK (distancia_a_puente_m IS NULL OR distancia_a_puente_m >= 0),
  ADD CONSTRAINT inspeccion_distancia_gps_ck
    CHECK (distancia_a_puente_m IS NULL OR ubicacion_inicio IS NOT NULL),
  -- DT-BD-02: las respuestas son un objeto JSON, nunca un arreglo ni un valor suelto
  ADD CONSTRAINT inspeccion_datos_objeto_ck
    CHECK (jsonb_typeof(datos) = 'object'),
  -- NOT VALID: rige para filas nuevas o modificadas; no escanea ni falla por datos previos
  ADD CONSTRAINT inspeccion_estado_confirmado_por_ck
    CHECK (estado_confirmado IS NULL OR estado_confirmado = estado_calculado
      OR estado_confirmado_por IS NOT NULL) NOT VALID;

COMMENT ON COLUMN inspeccion.distancia_a_puente_m IS 'Metros entre el GPS de inicio y el puente; más de 500 genera una advertencia (RN-INS-12). NULL si no hubo GPS';
COMMENT ON COLUMN inspeccion.creada_en_dispositivo IS 'Hora en que el dispositivo creó la inspección; creado_en es la del servidor al recibirla (RN-INS-13)';
COMMENT ON COLUMN inspeccion.estado_confirmado_por IS 'Quien confirmó un estado distinto del calculado (RN-IC-06, pendiente #12)';
COMMENT ON COLUMN inspeccion.version IS 'Contador de escrituras en el servidor, para detectar divergencia con el dispositivo y escrituras simultáneas (RN-OFF-09)';
COMMENT ON COLUMN inspeccion.ubicacion_inicio IS 'GPS del dispositivo al iniciar (RN-INS-12); NULL si no estuvo disponible';
COMMENT ON COLUMN inspeccion.enviada_en IS 'Fecha y hora en que el autor la envió a revisión; la del dispositivo si fue sin conexión (RN-INS-13)';
COMMENT ON COLUMN inspeccion.sincronizada_en IS 'Fecha y hora en que el servidor recibió y guardó la inspección (RN-INS-13)';
