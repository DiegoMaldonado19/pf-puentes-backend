-- DT-SEC-01: depuración de tokens vencidos
CREATE INDEX token_renovacion_expira_idx ON token_renovacion (expira_en);
