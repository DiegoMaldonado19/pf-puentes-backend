-- Semilla provisional de P5 (fase 0). No es el esquema SIECA oficial de P4.
INSERT INTO formulario_version (id, codigo, esquema, estado, publicada_en)
VALUES (
  '0199b000-0000-7000-8000-00000000f001',
  'SIECA-PRUEBA',
  '{"type":"object"}'::jsonb,
  'ACTIVA',
  now()
);
