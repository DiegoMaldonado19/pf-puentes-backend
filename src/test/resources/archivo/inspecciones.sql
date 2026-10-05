-- Un autor con un borrador reciente (b1), uno abandonado hace 211 días (b2, RN-ARC-06),
-- una publicada vieja (b3) y un borrador eliminado (b4). ON CONFLICT: el seed del INE puede traerlos.
INSERT INTO departamento VALUES ('09', 'Quetzaltenango') ON CONFLICT DO NOTHING;
INSERT INTO municipio VALUES ('0901', '09', 'Quetzaltenango') ON CONFLICT DO NOTHING;
INSERT INTO puente (id, codigo, nombre, municipio_codigo, ruta, ubicacion)
  VALUES ('0192f5a0-0000-7000-8000-000000000001', 'GT-09-0901-0001', 'Las Rosas', '0901', 'CA-1',
    'SRID=4326;POINT(-91.518 14.8347)');
INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol)
  VALUES ('0192f5a0-0000-7000-8000-000000000002', 'autor@cunoc.edu.gt', 'Autor', 'hash', 'ESTUDIANTE');
INSERT INTO formulario_version (id, codigo, esquema)
  VALUES ('0192f5a0-0000-7000-8000-000000000003', 'SIECA-1', '{}');

INSERT INTO inspeccion (id, puente_id, autor_id, formulario_version_id, fecha_inspeccion, actualizado_en, eliminado_en)
  VALUES
    ('0192f5a0-0000-7000-8000-0000000000b1', '0192f5a0-0000-7000-8000-000000000001',
      '0192f5a0-0000-7000-8000-000000000002', '0192f5a0-0000-7000-8000-000000000003', '2026-10-01', now(), NULL),
    ('0192f5a0-0000-7000-8000-0000000000b2', '0192f5a0-0000-7000-8000-000000000001',
      '0192f5a0-0000-7000-8000-000000000002', '0192f5a0-0000-7000-8000-000000000003', '2026-01-01',
      now() - interval '211 days', NULL),
    ('0192f5a0-0000-7000-8000-0000000000b4', '0192f5a0-0000-7000-8000-000000000001',
      '0192f5a0-0000-7000-8000-000000000002', '0192f5a0-0000-7000-8000-000000000003', '2026-10-01', now(), now());
INSERT INTO inspeccion (id, puente_id, autor_id, formulario_version_id, fecha_inspeccion, estado,
    indice_condicion, estado_calculado, estado_confirmado, publicada_en, actualizado_en)
  VALUES ('0192f5a0-0000-7000-8000-0000000000b3', '0192f5a0-0000-7000-8000-000000000001',
    '0192f5a0-0000-7000-8000-000000000002', '0192f5a0-0000-7000-8000-000000000003', '2026-01-01',
    'PUBLICADA', 80, 'BUENO', 'BUENO', now() - interval '211 days', now() - interval '211 days');

INSERT INTO archivo (id, inspeccion_id, tipo, clave, clave_miniatura, tamano)
  VALUES
    ('0192f5a0-0000-7000-8000-0000000000a1', '0192f5a0-0000-7000-8000-0000000000b1', 'WEBP',
      'prueba/reciente.webp', 'prueba/reciente-miniatura.webp', 10),
    ('0192f5a0-0000-7000-8000-0000000000a2', '0192f5a0-0000-7000-8000-0000000000b2', 'WEBP',
      'prueba/abandonada.webp', 'prueba/abandonada-miniatura.webp', 10),
    ('0192f5a0-0000-7000-8000-0000000000a3', '0192f5a0-0000-7000-8000-0000000000b2', 'PDF',
      'prueba/abandonada.pdf', NULL, 10),
    ('0192f5a0-0000-7000-8000-0000000000a4', '0192f5a0-0000-7000-8000-0000000000b3', 'JPEG',
      'prueba/publicada.jpg', 'prueba/publicada-miniatura.jpg', 10);
