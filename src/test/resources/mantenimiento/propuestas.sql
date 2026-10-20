-- Complementa ordenes.sql; datos de prueba, no catálogo ni generación automática en producción.
INSERT INTO orden_mantenimiento
 (id, puente_id, tipo, estado, descripcion, prioridad, fecha_ejecucion, responsable, motivo) VALUES
 ('019a0026-0000-7000-8000-000000000011', '019a0022-0000-7000-8000-000000000001', 'CORRECTIVO', 'PROPUESTA', 'Propuesta ficticia A1', 'ALTA', null, null, null),
 ('019a0026-0000-7000-8000-000000000012', '019a0022-0000-7000-8000-000000000001', 'PREVENTIVO', 'PROPUESTA', 'Propuesta ficticia A2', 'MEDIA', null, null, null),
 ('019a0026-0000-7000-8000-000000000013', '019a0022-0000-7000-8000-000000000002', 'RUTINARIO', 'PROPUESTA', 'Propuesta ficticia B', 'BAJA', null, null, null),
 ('019a0026-0000-7000-8000-000000000014', '019a0022-0000-7000-8000-000000000001', 'RUTINARIO', 'PROGRAMADA', 'Orden ficticia programada', 'BAJA', null, null, null),
 ('019a0026-0000-7000-8000-000000000015', '019a0022-0000-7000-8000-000000000001', 'RUTINARIO', 'EN_EJECUCION', 'Orden ficticia en ejecución', 'BAJA', null, null, null),
 ('019a0026-0000-7000-8000-000000000016', '019a0022-0000-7000-8000-000000000001', 'RUTINARIO', 'EJECUTADA', 'Orden histórica ficticia ejecutada', 'BAJA', '2026-10-19', 'Responsable de prueba', null),
 ('019a0026-0000-7000-8000-000000000017', '019a0022-0000-7000-8000-000000000001', 'RUTINARIO', 'DESCARTADA', 'Orden histórica ficticia descartada', 'BAJA', null, null, 'Motivo de prueba');
