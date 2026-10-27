DELETE FROM notificacion WHERE tipo = 'REVISION_VENCIDA' AND usuario_id IN ('0192f5a0-0000-7000-8000-000000000101', '0192f5a0-0000-7000-8000-000000000102');
DELETE FROM aviso_revision WHERE inspeccion_id IN (SELECT id FROM inspeccion WHERE formulario_version_id = '0199b700-0000-7000-8000-000000000001');
DELETE FROM auditoria WHERE entidad_id IN (
  SELECT o.id FROM observacion_anclada o JOIN revision r ON r.id = o.revision_id
  JOIN inspeccion i ON i.id = r.inspeccion_id WHERE i.formulario_version_id = '0199b700-0000-7000-8000-000000000001'
) OR entidad_id IN (
  SELECT id FROM revision WHERE inspeccion_id IN (SELECT id FROM inspeccion WHERE formulario_version_id = '0199b700-0000-7000-8000-000000000001')
) OR (entidad = 'inspeccion' AND entidad_id IN (SELECT id FROM inspeccion WHERE formulario_version_id = '0199b700-0000-7000-8000-000000000001'));
DELETE FROM observacion_anclada WHERE revision_id IN (SELECT id FROM revision WHERE inspeccion_id IN (SELECT id FROM inspeccion WHERE formulario_version_id = '0199b700-0000-7000-8000-000000000001'));
DELETE FROM transicion_inspeccion WHERE inspeccion_id IN (SELECT id FROM inspeccion WHERE formulario_version_id = '0199b700-0000-7000-8000-000000000001');
DELETE FROM revision WHERE inspeccion_id IN (SELECT id FROM inspeccion WHERE formulario_version_id = '0199b700-0000-7000-8000-000000000001');
-- Publicar también crea el hilo público del Foro, en la misma transacción.
-- Limitar la limpieza a los hilos de estas inspecciones; nunca desactivar el listener.
DELETE FROM auditoria WHERE entidad_id IN (
  SELECT h.id FROM hilo h JOIN inspeccion i ON i.id = h.inspeccion_id WHERE i.formulario_version_id = '0199b700-0000-7000-8000-000000000001'
) OR entidad_id IN (
  SELECT c.id FROM comentario c JOIN hilo h ON h.id = c.hilo_id JOIN inspeccion i ON i.id = h.inspeccion_id WHERE i.formulario_version_id = '0199b700-0000-7000-8000-000000000001'
);
DELETE FROM comentario WHERE hilo_id IN (
  SELECT h.id FROM hilo h JOIN inspeccion i ON i.id = h.inspeccion_id WHERE i.formulario_version_id = '0199b700-0000-7000-8000-000000000001'
) AND respuesta_a_id IS NOT NULL;
DELETE FROM comentario WHERE hilo_id IN (
  SELECT h.id FROM hilo h JOIN inspeccion i ON i.id = h.inspeccion_id WHERE i.formulario_version_id = '0199b700-0000-7000-8000-000000000001'
);
DELETE FROM hilo WHERE inspeccion_id IN (SELECT id FROM inspeccion WHERE formulario_version_id = '0199b700-0000-7000-8000-000000000001');
DELETE FROM inspeccion WHERE formulario_version_id = '0199b700-0000-7000-8000-000000000001';
DELETE FROM formulario_version WHERE id = '0199b700-0000-7000-8000-000000000001';
