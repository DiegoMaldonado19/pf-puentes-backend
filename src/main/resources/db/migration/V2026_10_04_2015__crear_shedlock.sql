-- DT-BE-14: esquema que exige ShedLock, no el de nuestras convenciones (ADR 0014)
CREATE TABLE shedlock (
  name VARCHAR(64) PRIMARY KEY,
  lock_until TIMESTAMP NOT NULL,
  locked_at TIMESTAMP NOT NULL,
  locked_by VARCHAR(255) NOT NULL
);

COMMENT ON TABLE shedlock IS 'Candados de los procesos programados; la escribe solo ShedLock (DT-BE-14, ADR 0014)';
COMMENT ON COLUMN shedlock.name IS 'Nombre del proceso, el de @SchedulerLock(name)';
COMMENT ON COLUMN shedlock.lock_until IS 'Hasta cuándo nadie más puede ejecutarlo, en UTC';
COMMENT ON COLUMN shedlock.locked_at IS 'Cuándo empezó la última ejecución, en UTC';
COMMENT ON COLUMN shedlock.locked_by IS 'Host de la réplica que lo ejecutó';
