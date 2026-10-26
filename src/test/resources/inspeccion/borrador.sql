UPDATE inspeccion SET datos = '{
  "identificacion": {
    "tipologia": "vigas",
    "material_principal": "concreto",
    "numero_tramos": 2,
    "longitud_m": 18.0,
    "ancho_calzada_m": 6.0,
    "anio_construccion": 1998
  },
  "subestructura": {"estribo_entrada": {"calificacion": 2, "fisuras": 1}}
}'::jsonb
WHERE id = '0199c200-0000-7000-8000-0000000000b1';

UPDATE inspeccion SET datos = '{
  "identificacion": {
    "tipologia": "losas",
    "material_principal": "acero",
    "numero_tramos": 3,
    "longitud_m": 25.5,
    "ancho_calzada_m": 7.2,
    "anio_construccion": 1998
  },
  "subestructura": {"estribo_entrada": {"calificacion": 4, "fisuras": 3, "humedad": 2}}
}'::jsonb
WHERE id = '0199c200-0000-7000-8000-0000000000b2';

UPDATE inspeccion SET datos = '{
  "identificacion": {
    "tipologia": "arco",
    "material_principal": "mamposteria",
    "numero_tramos": 1
  },
  "cauce": {"calificacion": 3}
}'::jsonb
WHERE id = '0199c200-0000-7000-8000-0000000000b3';

INSERT INTO inspeccion (
    id, puente_id, autor_id, formulario_version_id, estado, fecha_inspeccion,
    indice_condicion, estado_calculado, estado_confirmado, publicada_en, enviada_en, creado_en, datos)
VALUES
  ('0199c200-0000-7000-8000-0000000000be', '0199c200-0000-7000-8000-000000000002',
    '0199c200-0000-7000-8000-000000000015', '0199c200-0000-7000-8000-000000000021',
    'PUBLICADA', DATE '2026-04-01', 75, 'BUENO', 'BUENO',
    '2026-04-01T00:00:00Z', '2026-04-01T12:00:00Z', '2026-04-01T10:00:00Z',
    '{"identificacion":{"tipologia":"tigre-vieja","material_principal":"concreto"}}'::jsonb),
  ('0199c200-0000-7000-8000-0000000000bf', '0199c200-0000-7000-8000-000000000002',
    '0199c200-0000-7000-8000-000000000015', '0199c200-0000-7000-8000-000000000021',
    'PUBLICADA', DATE '2026-04-01', 78, 'BUENO', 'BUENO',
    '2026-04-03T00:00:00Z', '2026-04-01T18:00:00Z', '2026-04-01T16:00:00Z',
    '{"identificacion":{"tipologia":"tigre-nueva","material_principal":"acero"}}'::jsonb);
