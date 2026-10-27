DELETE FROM auditoria WHERE entidad_id IN (
  SELECT id FROM revision WHERE inspeccion_id IN (SELECT id FROM inspeccion WHERE formulario_version_id = '0199b700-0000-7000-8000-000000000001')
) OR (entidad = 'inspeccion' AND entidad_id IN (SELECT id FROM inspeccion WHERE formulario_version_id = '0199b700-0000-7000-8000-000000000001'));
DELETE FROM observacion_anclada WHERE revision_id IN (SELECT id FROM revision WHERE inspeccion_id IN (SELECT id FROM inspeccion WHERE formulario_version_id = '0199b700-0000-7000-8000-000000000001'));
DELETE FROM transicion_inspeccion WHERE inspeccion_id IN (SELECT id FROM inspeccion WHERE formulario_version_id = '0199b700-0000-7000-8000-000000000001');
DELETE FROM revision WHERE inspeccion_id IN (SELECT id FROM inspeccion WHERE formulario_version_id = '0199b700-0000-7000-8000-000000000001');
DELETE FROM inspeccion WHERE formulario_version_id = '0199b700-0000-7000-8000-000000000001';
DELETE FROM formulario_version WHERE id = '0199b700-0000-7000-8000-000000000001';
