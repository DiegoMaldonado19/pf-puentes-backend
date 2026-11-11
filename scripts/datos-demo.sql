-- Datos mínimos para calificar: una cuenta por rol, un curso y un puente asignado al estudiante.
-- Además, para la demostración de B8 y B9: pesos provisionales de V1.0.0 (sin ellos no hay IC ni
-- órdenes), dos puentes a menos de 100 m, un puente dado de baja y órdenes en varios estados.
-- Idempotente (ids fijos). Las cuentas copian el hash del primer administrador activo: entran con su
-- misma contraseña y ninguna clave queda en el repositorio. Las inspecciones se hacen en vivo.
BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM usuario WHERE rol = 'ADMINISTRADOR' AND estado = 'ACTIVO') THEN
    RAISE EXCEPTION 'No hay administrador activo: arranca el backend con ADMIN_INICIAL_*';
  END IF;
END $$;

WITH admin AS (
  SELECT id, contrasena_hash FROM usuario
  WHERE rol = 'ADMINISTRADOR' AND estado = 'ACTIVO'
  ORDER BY creado_en
  LIMIT 1
)
INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol, estado, correo_verificado_en,
                     numero_colegiado, colegiado_verificado_en, colegiado_verificado_por)
SELECT v.id, v.correo, v.nombre, admin.contrasena_hash, v.rol, 'ACTIVO', now(), v.colegiado,
       CASE WHEN v.colegiado IS NULL THEN NULL ELSE now() END,
       CASE WHEN v.colegiado IS NULL THEN NULL ELSE admin.id END
FROM admin, (VALUES
  ('019a2a00-0000-7000-8000-000000000001'::uuid, 'catedratico.demo@sgp.gt', 'Catedrático Demo',
    'CATEDRATICO', NULL),
  ('019a2a00-0000-7000-8000-000000000002'::uuid, 'estudiante.demo@sgp.gt', 'Estudiante Demo',
    'ESTUDIANTE', NULL),
  ('019a2a00-0000-7000-8000-000000000003'::uuid, 'profesional.demo@sgp.gt', 'Profesional Demo',
    'PROFESIONAL_EXTERNO', 'DEMO-0001')
) AS v(id, correo, nombre, rol, colegiado)
ON CONFLICT DO NOTHING;

-- Correlativo 99xx: lejos de los que genera el inventario para el municipio
INSERT INTO puente (id, codigo, nombre, municipio_codigo, ruta, ubicacion, anio_construccion, activo)
VALUES ('019a2a00-0000-7000-8000-000000000010', 'GT-09-0901-9901', 'Puente Demo Samalá', '0901',
  'CA-1', 'SRID=4326;POINT(-91.518 14.836)', 1985, true)
ON CONFLICT DO NOTHING;

INSERT INTO curso (id, nombre, anio, semestre, catedratico_id, vigente)
VALUES ('019a2a00-0000-7000-8000-000000000020', 'Puentes Demo', 2026, 2,
  '019a2a00-0000-7000-8000-000000000001', true)
ON CONFLICT DO NOTHING;

INSERT INTO inscripcion (id, curso_id, estudiante_id)
VALUES ('019a2a00-0000-7000-8000-000000000030', '019a2a00-0000-7000-8000-000000000020',
  '019a2a00-0000-7000-8000-000000000002')
ON CONFLICT DO NOTHING;

INSERT INTO asignacion (id, inscripcion_id, puente_id)
VALUES ('019a2a00-0000-7000-8000-000000000040', '019a2a00-0000-7000-8000-000000000030',
  '019a2a00-0000-7000-8000-000000000010')
ON CONFLICT DO NOTHING;

-- Pesos provisionales para la versión semilla V1.0.0: un peso por cada campo de daño, como exige el
-- servidor. El acta es NULL: el juego es provisional y no sustituye la calibración del Catedrático.
INSERT INTO version_pesos (id, formulario_version_id, acta, vigente_desde)
VALUES ('019a2a00-0000-7000-8000-000000000050', '0192b000-0000-7000-8000-000000000001', NULL, now())
ON CONFLICT DO NOTHING;

INSERT INTO peso_elemento (id, version_pesos_id, elemento_ref, peso)
SELECT ('019a2a00-0000-7000-8000-0000000001' || lpad(r.n::text, 2, '0'))::uuid,
  '019a2a00-0000-7000-8000-000000000050', r.ref, 1.000
FROM (VALUES
  (1, 'caracteristicas_cauce.alineamiento'),
  (2, 'caracteristicas_cauce.obstruccion'),
  (3, 'caracteristicas_cauce.proteccion_margenes'),
  (4, 'elementos_no_estructurales.aceras.danos'),
  (5, 'elementos_no_estructurales.barandas.danos'),
  (6, 'elementos_no_estructurales.drenajes.danos'),
  (7, 'subestructura.cimentacion.asentamiento'),
  (8, 'subestructura.estribo_entrada.grietas'),
  (9, 'subestructura.estribo_entrada.socavacion'),
  (10, 'subestructura.estribo_salida.grietas'),
  (11, 'subestructura.estribo_salida.socavacion'),
  (12, 'subestructura.pilas.grietas'),
  (13, 'subestructura.pilas.socavacion'),
  (14, 'superestructura.apoyos.deterioro'),
  (15, 'superestructura.juntas.deterioro'),
  (16, 'superestructura.losa.agrietamiento'),
  (17, 'superestructura.vigas.ausencia_elemento'),
  (18, 'superestructura.vigas.deformacion'),
  (19, 'superestructura.vigas.perdida_seccion')
) AS r(n, ref)
ON CONFLICT DO NOTHING;

-- Dos puentes a menos de 100 m del de demostración (posibles duplicados, B8) y uno dado de baja
-- con fecha, autor y motivo (RN-INV-08, B6).
INSERT INTO puente (id, codigo, nombre, municipio_codigo, ruta, ubicacion, anio_construccion, activo,
  baja_en, baja_por, motivo_baja)
VALUES
  ('019a2a00-0000-7000-8000-000000000011', 'GT-09-0901-9902', 'Puente Demo Samalá norte', '0901',
    'CA-1', 'SRID=4326;POINT(-91.518 14.8365)', 1990, true, NULL, NULL, NULL),
  ('019a2a00-0000-7000-8000-000000000012', 'GT-09-0901-9903', 'Puente Demo retirado', '0901',
    'RD-2', 'SRID=4326;POINT(-91.402 14.690)', 1972, false, now(),
    (SELECT id FROM usuario WHERE rol = 'ADMINISTRADOR' AND estado = 'ACTIVO' ORDER BY creado_en
      LIMIT 1),
    'Demostración: retirado del servicio por falla en la superestructura')
ON CONFLICT DO NOTHING;

-- Órdenes de mantenimiento en cuatro estados para el puente de demostración (B9).
INSERT INTO orden_mantenimiento (id, puente_id, tipo, estado, descripcion, elemento_ref, prioridad,
  fecha_programada, fecha_ejecucion, responsable, motivo)
VALUES
  ('019a2a00-0000-7000-8000-000000000060', '019a2a00-0000-7000-8000-000000000010', 'CORRECTIVO',
    'PROPUESTA', 'Reparar grietas en la pila 1', 'subestructura.pilas.grietas', 'ALTA',
    NULL, NULL, NULL, NULL),
  ('019a2a00-0000-7000-8000-000000000061', '019a2a00-0000-7000-8000-000000000010', 'PREVENTIVO',
    'PROGRAMADA', 'Limpiar el cauce antes de la temporada de lluvias',
    'caracteristicas_cauce.obstruccion', 'MEDIA', DATE '2026-12-15', NULL, NULL, NULL),
  ('019a2a00-0000-7000-8000-000000000062', '019a2a00-0000-7000-8000-000000000010', 'RUTINARIO',
    'EJECUTADA', 'Revisión anual de juntas de dilatación', 'superestructura.juntas.deterioro',
    'BAJA', NULL, DATE '2026-10-30', 'Profesional Demo', NULL),
  ('019a2a00-0000-7000-8000-000000000063', '019a2a00-0000-7000-8000-000000000010', 'EMERGENCIA',
    'DESCARTADA', 'Atender socavación reportada en el estribo',
    'subestructura.estribo_entrada.socavacion', 'ALTA', NULL, NULL, NULL,
    'Verificado en campo: sin riesgo; se mantiene en seguimiento')
ON CONFLICT DO NOTHING;

COMMIT;
