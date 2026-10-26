-- RN-INS-07, DT-BD-11: la aplicación no borra inspecciones y el historial de estados solo se inserta
DO $$
BEGIN
  -- Igual que V2026_10_10_1005: el rol solo existe en los ambientes con docker/postgres/01-usuario-app.sh
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'puentes_app') THEN
    REVOKE DELETE ON inspeccion FROM puentes_app;
    REVOKE UPDATE, DELETE ON transicion_inspeccion FROM puentes_app;
  END IF;
END
$$;
