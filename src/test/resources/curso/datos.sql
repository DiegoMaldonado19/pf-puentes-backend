INSERT INTO departamento VALUES ('99', 'Departamento de prueba P7') ON CONFLICT DO NOTHING;
INSERT INTO municipio VALUES ('9901', '99', 'Municipio de prueba P7') ON CONFLICT DO NOTHING;
INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol, estado, correo_verificado_en) VALUES
('0192f5a0-0000-7000-8000-000000000101', 'docente1@test.edu', 'Docente propio', 'hash', 'CATEDRATICO', 'ACTIVO', now()),
('0192f5a0-0000-7000-8000-000000000102', 'docente2@test.edu', 'Docente ajeno', 'hash', 'CATEDRATICO', 'ACTIVO', now()),
('0192f5a0-0000-7000-8000-000000000103', 'alumno1@test.edu', 'Estudiante activo', 'hash', 'ESTUDIANTE', 'ACTIVO', now()),
('0192f5a0-0000-7000-8000-000000000104', 'alumno2@test.edu', 'Estudiante ajeno', 'hash', 'ESTUDIANTE', 'ACTIVO', now()),
('0192f5a0-0000-7000-8000-000000000105', 'pendiente@test.edu', 'Estudiante pendiente', 'hash', 'ESTUDIANTE', 'PENDIENTE', now()),
('0192f5a0-0000-7000-8000-000000000106', 'inactivo@test.edu', 'Estudiante inactivo', 'hash', 'ESTUDIANTE', 'INACTIVO', now()),
('0192f5a0-0000-7000-8000-000000000107', 'noverificado@test.edu', 'Sin verificar', 'hash', 'ESTUDIANTE', 'PENDIENTE', NULL);
INSERT INTO puente (id, codigo, nombre, municipio_codigo, ruta, ubicacion, activo, motivo_baja) VALUES
('0192f5a0-0000-7000-8000-000000000201', 'GT-99-9901-0201', 'Puente activo', '9901', 'CA-1', 'SRID=4326;POINT(-91.51 14.83)', true, NULL),
('0192f5a0-0000-7000-8000-000000000202', 'GT-99-9901-0202', 'Puente inactivo', '9901', 'CA-1', 'SRID=4326;POINT(-91.52 14.84)', false, 'Demolido');
INSERT INTO curso (id, nombre, anio, semestre, catedratico_id, vigente) VALUES
('0192f5a0-0000-7000-8000-000000000301', 'Puentes propio', 2026, 2, '0192f5a0-0000-7000-8000-000000000101', true),
('0192f5a0-0000-7000-8000-000000000302', 'Puentes ajeno', 2026, 2, '0192f5a0-0000-7000-8000-000000000102', true),
('0192f5a0-0000-7000-8000-000000000303', 'Puentes cerrado', 2026, 1, '0192f5a0-0000-7000-8000-000000000101', false);
INSERT INTO inscripcion (id, curso_id, estudiante_id) VALUES
('0192f5a0-0000-7000-8000-000000000401', '0192f5a0-0000-7000-8000-000000000301', '0192f5a0-0000-7000-8000-000000000103'),
('0192f5a0-0000-7000-8000-000000000402', '0192f5a0-0000-7000-8000-000000000302', '0192f5a0-0000-7000-8000-000000000104'),
('0192f5a0-0000-7000-8000-000000000403', '0192f5a0-0000-7000-8000-000000000303', '0192f5a0-0000-7000-8000-000000000103');
INSERT INTO asignacion (id, inscripcion_id, puente_id) VALUES
('0192f5a0-0000-7000-8000-000000000501', '0192f5a0-0000-7000-8000-000000000401', '0192f5a0-0000-7000-8000-000000000201'),
('0192f5a0-0000-7000-8000-000000000502', '0192f5a0-0000-7000-8000-000000000402', '0192f5a0-0000-7000-8000-000000000201'),
('0192f5a0-0000-7000-8000-000000000503', '0192f5a0-0000-7000-8000-000000000403', '0192f5a0-0000-7000-8000-000000000201');
