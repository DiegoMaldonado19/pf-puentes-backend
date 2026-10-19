-- Datos exclusivos de pruebas. No son propuestas de un catálogo aprobado ni publicación real de P5.
INSERT INTO departamento (codigo, nombre) VALUES ('98', 'Departamento ficticio');
INSERT INTO municipio (codigo, departamento_codigo, nombre) VALUES ('9898', '98', 'Municipio ficticio');
INSERT INTO puente (id, codigo, nombre, municipio_codigo, ruta, ubicacion) VALUES
 ('019a0025-0000-7000-8000-000000000001', 'GT-98-9898-9901', 'Puente de aceptación A', '9898', 'Ruta ficticia', 'SRID=4326;POINT(-90.51 14.62)'),
 ('019a0025-0000-7000-8000-000000000002', 'GT-98-9898-9902', 'Puente de aceptación B', '9898', 'Ruta ficticia', 'SRID=4326;POINT(-90.52 14.63)');
INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol, estado, correo_verificado_en, numero_colegiado, colegiado_verificado_en, colegiado_verificado_por) VALUES
 ('019a0025-0000-7000-8000-000000000003', 'admin@aceptacion-prueba.com', 'Admin ficticio', 'hash-no-utilizable', 'ADMINISTRADOR', 'ACTIVO', '2026-10-19T12:00:00Z', null, null, null),
 ('019a0025-0000-7000-8000-000000000004', 'catedratico@aceptacion-prueba.com', 'Catedrático ficticio', 'hash-no-utilizable', 'CATEDRATICO', 'ACTIVO', '2026-10-19T12:00:00Z', null, null, null),
 ('019a0025-0000-7000-8000-000000000005', 'estudiante@aceptacion-prueba.com', 'Estudiante ficticio', 'hash-no-utilizable', 'ESTUDIANTE', 'ACTIVO', '2026-10-19T12:00:00Z', null, null, null),
 ('019a0025-0000-7000-8000-000000000006', 'profesional@aceptacion-prueba.com', 'Profesional ficticio', 'hash-no-utilizable', 'PROFESIONAL_EXTERNO', 'ACTIVO', '2026-10-19T12:00:00Z', 'PRUEBA-P8-25', '2026-10-19T12:00:00Z', '019a0025-0000-7000-8000-000000000003');
INSERT INTO formulario_version (id, codigo, esquema) VALUES
 ('019a0025-0000-7000-8000-000000000007', 'P8-ACEPTACION-PRUEBA', '{"type":"object"}');
INSERT INTO inspeccion (id, puente_id, autor_id, formulario_version_id, estado, fecha_inspeccion,
 publicada_en, indice_condicion, estado_calculado, estado_confirmado) VALUES
 ('019a0025-0000-7000-8000-000000000008', '019a0025-0000-7000-8000-000000000001',
 '019a0025-0000-7000-8000-000000000005', '019a0025-0000-7000-8000-000000000007',
 'PUBLICADA', '2026-10-19', '2026-10-19T12:00:00Z', 75, 'BUENO', 'BUENO');
INSERT INTO orden_mantenimiento (id, puente_id, inspeccion_id, tipo, estado, descripcion, elemento_ref,
 prioridad_sugerida, prioridad, justificacion_prioridad, fecha_programada) VALUES
 ('019a0025-0000-7000-8000-000000000010', '019a0025-0000-7000-8000-000000000001',
 '019a0025-0000-7000-8000-000000000008', 'CORRECTIVO', 'PROPUESTA', 'Intervención ficticia', 'elemento-de-prueba',
 'MEDIA', 'ALTA', 'Justificación previa', '2026-10-25');
INSERT INTO orden_mantenimiento (id, puente_id, tipo, estado, descripcion, prioridad, fecha_ejecucion, responsable, motivo) VALUES
 ('019a0025-0000-7000-8000-000000000011', '019a0025-0000-7000-8000-000000000001', 'RUTINARIO', 'PROGRAMADA', 'Orden ficticia', 'MEDIA', null, null, null),
 ('019a0025-0000-7000-8000-000000000012', '019a0025-0000-7000-8000-000000000001', 'RUTINARIO', 'EN_EJECUCION', 'Orden ficticia', 'MEDIA', null, null, null),
 ('019a0025-0000-7000-8000-000000000013', '019a0025-0000-7000-8000-000000000001', 'RUTINARIO', 'EJECUTADA', 'Orden histórica ficticia', 'MEDIA', '2026-10-18', 'Responsable ficticio', null),
 ('019a0025-0000-7000-8000-000000000014', '019a0025-0000-7000-8000-000000000001', 'RUTINARIO', 'DESCARTADA', 'Orden histórica ficticia', 'MEDIA', null, null, 'Motivo previo'),
 ('019a0025-0000-7000-8000-000000000015', '019a0025-0000-7000-8000-000000000001', 'RUTINARIO', 'CANCELADA', 'Orden histórica ficticia', 'MEDIA', null, null, 'Motivo previo'),
 ('019a0025-0000-7000-8000-000000000016', '019a0025-0000-7000-8000-000000000002', 'RUTINARIO', 'PROPUESTA', 'Orden sin fecha ficticia', 'MEDIA', null, null, null);
