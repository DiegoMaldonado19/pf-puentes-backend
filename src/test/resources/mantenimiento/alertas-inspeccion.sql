-- Datos exclusivos de pruebas. No son propuestas de un catálogo aprobado ni publicación real de P5.
INSERT INTO departamento (codigo, nombre) VALUES ('92', 'Departamento ficticio');
INSERT INTO municipio (codigo, departamento_codigo, nombre) VALUES ('9292', '92', 'Municipio ficticio');
INSERT INTO puente (id, codigo, nombre, municipio_codigo, ruta, ubicacion) VALUES
 ('019a0034-0000-7000-8000-000000000001', 'GT-92-9292-9901', 'Puente de inicio A', '9292', 'Ruta ficticia', 'SRID=4326;POINT(-90.51 14.62)'),
 ('019a0034-0000-7000-8000-000000000002', 'GT-92-9292-9902', 'Puente de inicio B', '9292', 'Ruta ficticia', 'SRID=4326;POINT(-90.52 14.63)');
INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol, estado, correo_verificado_en, numero_colegiado, colegiado_verificado_en, colegiado_verificado_por) VALUES
 ('019a0034-0000-7000-8000-000000000003', 'admin@alertas-prueba.com', 'Admin ficticio', 'hash-no-utilizable', 'ADMINISTRADOR', 'ACTIVO', '2026-10-19T12:00:00Z', null, null, null),
 ('019a0034-0000-7000-8000-000000000004', 'catedratico@alertas-prueba.com', 'Catedrático ficticio', 'hash-no-utilizable', 'CATEDRATICO', 'ACTIVO', '2026-10-19T12:00:00Z', null, null, null),
 ('019a0034-0000-7000-8000-000000000005', 'estudiante@alertas-prueba.com', 'Estudiante ficticio', 'hash-no-utilizable', 'ESTUDIANTE', 'ACTIVO', '2026-10-19T12:00:00Z', null, null, null),
 ('019a0034-0000-7000-8000-000000000006', 'profesional@alertas-prueba.com', 'Profesional ficticio', 'hash-no-utilizable', 'PROFESIONAL_EXTERNO', 'ACTIVO', '2026-10-19T12:00:00Z', 'PRUEBA-P8-34', '2026-10-19T12:00:00Z', '019a0034-0000-7000-8000-000000000003');
INSERT INTO formulario_version (id, codigo, esquema) VALUES
 ('019a0034-0000-7000-8000-000000000007', 'P8-ALERTA-PRUEBA', '{"type":"object"}');
INSERT INTO inspeccion (id, puente_id, autor_id, formulario_version_id, estado, fecha_inspeccion,
 publicada_en, indice_condicion, estado_calculado, estado_confirmado) VALUES
 ('019a0034-0000-7000-8000-000000000008', '019a0034-0000-7000-8000-000000000001',
 '019a0034-0000-7000-8000-000000000005', '019a0034-0000-7000-8000-000000000007',
 'PUBLICADA', '2023-10-19', '2024-10-21T12:00:00Z', 75, 'BUENO', 'BUENO');
UPDATE puente SET creado_en = '2024-10-21T12:00:00Z' WHERE municipio_codigo = '9292';
