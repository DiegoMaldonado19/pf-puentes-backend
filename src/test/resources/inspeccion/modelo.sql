-- Catálogo mínimo para US-P5-01. ON CONFLICT: el seed INE o otras pruebas pueden traer el municipio.
INSERT INTO departamento VALUES ('09', 'Quetzaltenango') ON CONFLICT DO NOTHING;
INSERT INTO municipio VALUES ('0901', '09', 'Quetzaltenango') ON CONFLICT DO NOTHING;
INSERT INTO puente (id, codigo, nombre, municipio_codigo, ruta, ubicacion)
  VALUES
    ('0199c000-0000-7000-8000-000000000001', 'GT-09-0901-9001', 'Las Rosas P5', '0901', 'CA-1',
      'SRID=4326;POINT(-91.518 14.8347)'),
    ('0199c000-0000-7000-8000-000000000002', 'GT-09-0901-9002', 'El Tigre P5', '0901', 'CA-1',
      'SRID=4326;POINT(-91.520 14.8400)');
INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol)
  VALUES
    ('0199c000-0000-7000-8000-000000000011', 'autor-p5@cunoc.edu.gt', 'Autor P5', 'hash', 'ESTUDIANTE'),
    ('0199c000-0000-7000-8000-000000000012', 'par-p5@cunoc.edu.gt', 'Acompañante P5', 'hash', 'ESTUDIANTE'),
    ('0199c000-0000-7000-8000-000000000013', 'catedratico-p5@cunoc.edu.gt', 'Catedrático P5', 'hash', 'CATEDRATICO');
INSERT INTO formulario_version (id, codigo, esquema)
  VALUES ('0199c000-0000-7000-8000-000000000021', 'SIECA-P5-01', '{}');
INSERT INTO curso (id, nombre, anio, semestre, catedratico_id, vigente)
  VALUES ('0199c000-0000-7000-8000-000000000031', 'Puentes P5', 2026, 2,
    '0199c000-0000-7000-8000-000000000013', true);
