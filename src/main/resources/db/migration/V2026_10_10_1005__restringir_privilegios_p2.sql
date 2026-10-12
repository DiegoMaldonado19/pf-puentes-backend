-- RN-USR-08 / RN-USR-09 / DT-BD-11: la aplicación no borra cuentas ni altera la bitácora
DO $$
BEGIN
  -- El rol existe en los ambientes con docker/postgres/01-usuario-app.sh;
  -- en otros (por ejemplo Testcontainers, con un solo usuario) no hay nada que restringir
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'puentes_app') THEN
    REVOKE UPDATE, DELETE ON auditoria FROM puentes_app;
    REVOKE DELETE ON usuario FROM puentes_app;
  END IF;
END
$$;
