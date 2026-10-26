INSERT INTO departamento VALUES ('09', 'Quetzaltenango') ON CONFLICT DO NOTHING;
INSERT INTO municipio VALUES ('0901', '09', 'Quetzaltenango') ON CONFLICT DO NOTHING;
INSERT INTO puente (id, codigo, nombre, municipio_codigo, ruta, ubicacion)
  VALUES ('0199c100-0000-7000-8000-000000000001', 'GT-09-0901-9101', 'Las Rosas Máquina', '0901', 'CA-1',
    'SRID=4326;POINT(-91.518 14.8347)');
INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol, estado, correo_verificado_en)
  VALUES
    ('0199c100-0000-7000-8000-000000000011', 'est-maquina@cunoc.edu.gt', 'Estudiante', 'hash',
      'ESTUDIANTE', 'ACTIVO', now()),
    ('0199c100-0000-7000-8000-000000000013', 'rev-maquina@cunoc.edu.gt', 'Revisor', 'hash',
      'CATEDRATICO', 'ACTIVO', now()),
    ('0199c100-0000-7000-8000-000000000014', 'cat-maquina@cunoc.edu.gt', 'Catedrático autor', 'hash',
      'CATEDRATICO', 'ACTIVO', now()),
    ('0199c100-0000-7000-8000-000000000016', 'otro-maquina@cunoc.edu.gt', 'Otro', 'hash',
      'ESTUDIANTE', 'ACTIVO', now());
INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol, estado, correo_verificado_en,
    numero_colegiado, colegiado_verificado_en, colegiado_verificado_por)
  VALUES ('0199c100-0000-7000-8000-000000000015', 'pro-maquina@cunoc.edu.gt', 'Profesional', 'hash',
    'PROFESIONAL_EXTERNO', 'ACTIVO', now(), '15001', now(),
    '0199c100-0000-7000-8000-000000000013');
INSERT INTO formulario_version (id, codigo, esquema)
  VALUES ('0199c100-0000-7000-8000-000000000021', 'SIECA-P5-02', '{}');
