-- Datos mínimos para calificar: una cuenta por rol, un curso y un puente asignado al estudiante.
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

COMMIT;
