-- RN-INV-03: el correlativo es por municipio y solo avanza, así que un número nunca se reutiliza
-- aunque el puente se dé de baja. Una fila por municipio; la reserva es un UPSERT atómico.
CREATE TABLE correlativo_puente (
  municipio_codigo VARCHAR(4) PRIMARY KEY REFERENCES municipio,
  ultimo INTEGER NOT NULL CHECK (ultimo BETWEEN 0 AND 9999)
);

-- Continúa desde los códigos que ya existan para no repetirlos
INSERT INTO correlativo_puente (municipio_codigo, ultimo)
SELECT municipio_codigo, max(right(codigo, 4)::INTEGER)
FROM puente
GROUP BY municipio_codigo;

COMMENT ON TABLE correlativo_puente IS 'Último correlativo asignado a un código de puente en cada municipio (RN-INV-03)';
COMMENT ON COLUMN correlativo_puente.municipio_codigo IS 'Municipio INE del correlativo';
COMMENT ON COLUMN correlativo_puente.ultimo IS 'Último número usado; el siguiente puente recibe ultimo + 1 (máximo 9999)';
