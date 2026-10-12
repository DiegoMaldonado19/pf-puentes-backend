-- Solo elimina los fixtures de P7 en el PostgreSQL efímero de Testcontainers.
DELETE FROM auditoria WHERE usuario_id IN ('0192f5a0-0000-7000-8000-000000000101', '0192f5a0-0000-7000-8000-000000000102');
-- La auditoría común atribuye llamadas internas sin sesión al Sistema (usuario_id NULL).
-- También se limpian esas filas por la entidad de los fixtures, nunca toda la bitácora.
DELETE FROM auditoria WHERE
  (entidad = 'curso' AND entidad_id IN (
    SELECT id FROM curso WHERE catedratico_id IN ('0192f5a0-0000-7000-8000-000000000101', '0192f5a0-0000-7000-8000-000000000102')
  )) OR
  (entidad = 'inscripcion' AND entidad_id IN (
    SELECT i.id FROM inscripcion i JOIN curso c ON c.id = i.curso_id
    WHERE c.catedratico_id IN ('0192f5a0-0000-7000-8000-000000000101', '0192f5a0-0000-7000-8000-000000000102')
  )) OR
  (entidad = 'asignacion' AND entidad_id IN (
    SELECT a.id FROM asignacion a JOIN inscripcion i ON i.id = a.inscripcion_id JOIN curso c ON c.id = i.curso_id
    WHERE c.catedratico_id IN ('0192f5a0-0000-7000-8000-000000000101', '0192f5a0-0000-7000-8000-000000000102')
  ));
DELETE FROM asignacion WHERE inscripcion_id IN (
  SELECT i.id FROM inscripcion i JOIN curso c ON c.id = i.curso_id
  WHERE c.catedratico_id IN ('0192f5a0-0000-7000-8000-000000000101', '0192f5a0-0000-7000-8000-000000000102')
);
DELETE FROM inscripcion WHERE curso_id IN (
  SELECT id FROM curso WHERE catedratico_id IN ('0192f5a0-0000-7000-8000-000000000101', '0192f5a0-0000-7000-8000-000000000102')
);
DELETE FROM curso WHERE catedratico_id IN ('0192f5a0-0000-7000-8000-000000000101', '0192f5a0-0000-7000-8000-000000000102');
DELETE FROM puente WHERE id IN ('0192f5a0-0000-7000-8000-000000000201', '0192f5a0-0000-7000-8000-000000000202');
DELETE FROM usuario WHERE id IN (
  '0192f5a0-0000-7000-8000-000000000101', '0192f5a0-0000-7000-8000-000000000102',
  '0192f5a0-0000-7000-8000-000000000103', '0192f5a0-0000-7000-8000-000000000104',
  '0192f5a0-0000-7000-8000-000000000105', '0192f5a0-0000-7000-8000-000000000106',
  '0192f5a0-0000-7000-8000-000000000107'
);
DELETE FROM municipio WHERE codigo = '9901';
DELETE FROM departamento WHERE codigo = '99';
