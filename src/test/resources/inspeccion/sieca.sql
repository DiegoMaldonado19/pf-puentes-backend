-- V1.0.0 es la única versión ACTIVA y lleva un juego de pesos con sus 19 referencias de daño.
UPDATE formulario_version SET estado = 'RETIRADA' WHERE estado = 'ACTIVA';
UPDATE formulario_version SET estado = 'ACTIVA' WHERE id = '0192b000-0000-7000-8000-000000000001';

INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol, estado, correo_verificado_en)
VALUES
  ('0199c300-0000-7000-8000-000000000001', 'estudiante-sieca@cunoc.edu.gt', 'Estudiante SIECA', 'hash', 'ESTUDIANTE', 'ACTIVO', now()),
  ('0199c300-0000-7000-8000-000000000002', 'catedratico-sieca@cunoc.edu.gt', 'Catedrático SIECA', 'hash', 'CATEDRATICO', 'ACTIVO', now());

INSERT INTO puente (id, codigo, nombre, municipio_codigo, ruta, ubicacion, anio_construccion, activo)
VALUES
  ('0199c300-0000-7000-8000-000000000010', 'GT-09-0901-9301', 'Puente SIECA', '0901', 'CA-1',
    'SRID=4326;POINT(-91.515 14.835)', 1990, true);

INSERT INTO curso (id, nombre, anio, semestre, catedratico_id, vigente)
VALUES
  ('0199c300-0000-7000-8000-000000000020', 'Puentes SIECA', 2026, 2,
    '0199c300-0000-7000-8000-000000000002', true);

INSERT INTO inscripcion (id, curso_id, estudiante_id)
VALUES
  ('0199c300-0000-7000-8000-000000000030', '0199c300-0000-7000-8000-000000000020',
    '0199c300-0000-7000-8000-000000000001');

INSERT INTO asignacion (id, inscripcion_id, puente_id)
VALUES
  ('0199c300-0000-7000-8000-000000000040', '0199c300-0000-7000-8000-000000000030',
    '0199c300-0000-7000-8000-000000000010');

INSERT INTO version_pesos (id, formulario_version_id)
VALUES
  ('0199c300-0000-7000-8000-000000000050', '0192b000-0000-7000-8000-000000000001');

INSERT INTO peso_elemento (id, version_pesos_id, elemento_ref, peso)
SELECT gen_random_uuid(), '0199c300-0000-7000-8000-000000000050'::uuid, ref, 1
FROM unnest(ARRAY[
  'subestructura.estribo_entrada.grietas', 'subestructura.estribo_entrada.socavacion',
  'subestructura.estribo_salida.grietas', 'subestructura.estribo_salida.socavacion',
  'subestructura.pilas.grietas', 'subestructura.pilas.socavacion',
  'subestructura.cimentacion.asentamiento',
  'superestructura.vigas.deformacion', 'superestructura.vigas.perdida_seccion',
  'superestructura.vigas.ausencia_elemento', 'superestructura.losa.agrietamiento',
  'superestructura.juntas.deterioro', 'superestructura.apoyos.deterioro',
  'elementos_no_estructurales.barandas.danos', 'elementos_no_estructurales.aceras.danos',
  'elementos_no_estructurales.drenajes.danos', 'caracteristicas_cauce.obstruccion',
  'caracteristicas_cauce.alineamiento', 'caracteristicas_cauce.proteccion_margenes'
]) AS ref;
