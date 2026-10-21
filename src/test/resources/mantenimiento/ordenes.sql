-- Fixtures aislados de persistencia, no catálogo oficial ni publicación real de P5.
INSERT INTO puente (id, codigo, nombre, municipio_codigo, ruta, ubicacion) VALUES
 ('019a0022-0000-7000-8000-000000000001', 'GT-01-0101-9901', 'Puente ficticio A', '0101', 'Ruta de prueba', 'SRID=4326;POINT(-90.51 14.62)'),
 ('019a0022-0000-7000-8000-000000000002', 'GT-01-0101-9902', 'Puente ficticio B', '0101', 'Ruta de prueba', 'SRID=4326;POINT(-90.52 14.63)');
INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol) VALUES
 ('019a0022-0000-7000-8000-000000000003', 'autor@mantenimiento-prueba.com', 'Autor ficticio', 'hash-no-utilizable', 'ESTUDIANTE');
INSERT INTO formulario_version (id, codigo, esquema) VALUES
 ('019a0022-0000-7000-8000-000000000004', 'P8-MTO-PRUEBA', '{"type":"object"}');
INSERT INTO inspeccion (id, puente_id, autor_id, formulario_version_id, estado, fecha_inspeccion,
 publicada_en, indice_condicion, estado_calculado, estado_confirmado) VALUES
 ('019a0022-0000-7000-8000-000000000005', '019a0022-0000-7000-8000-000000000001',
  '019a0022-0000-7000-8000-000000000003', '019a0022-0000-7000-8000-000000000004',
  'PUBLICADA', '2026-10-19', '2026-10-19T12:00:00Z', 75, 'BUENO', 'BUENO');
-- Orden histórica sintética: consultar CANCELADA no habilita una transición cuyo origen falta acordar.
INSERT INTO orden_mantenimiento (id, puente_id, tipo, estado, descripcion, prioridad, motivo) VALUES
 ('019a0022-0000-7000-8000-000000000010', '019a0022-0000-7000-8000-000000000001',
  'PREVENTIVO', 'CANCELADA', 'Orden histórica ficticia', 'MEDIA', 'Motivo histórico de prueba');
