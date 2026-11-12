-- SOLO dev. Lo corre el pipeline después de datos-demo.sql: deja al primer administrador activo y a
-- las cuentas de demostración con la contraseña conocida `password123456` (BCrypt, factor 12) y sin
-- bloqueo por intentos fallidos. Nunca en stage ni en prod: la clave es pública.
UPDATE usuario
SET contrasena_hash = '$2a$12$yn58VJFLS4xxxzbwHi4k/ODsh.fJ9glZGQXo5z.zRHSpPADti6D/u',
    intentos_fallidos = 0,
    primer_fallo_en = NULL,
    bloqueado_hasta = NULL
WHERE id::text LIKE '019a2a00-%'
   OR id = (SELECT id FROM usuario
            WHERE rol = 'ADMINISTRADOR' AND estado = 'ACTIVO'
            ORDER BY creado_en
            LIMIT 1);
