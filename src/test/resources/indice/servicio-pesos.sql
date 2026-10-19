-- Datos de prueba: estos esquemas y referencias no sustituyen el contrato pendiente de P4.
INSERT INTO formulario_version (id, codigo, esquema, estado) VALUES
  ('019a0018-0000-7000-8000-000000000001', 'P8-SERVICIO-A', '{"type":"object"}', 'BORRADOR'),
  ('019a0018-0000-7000-8000-000000000002', 'P8-SERVICIO-B', '{"type":"object"}', 'BORRADOR');

INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol, estado, correo_verificado_en)
VALUES ('019a0018-0000-7000-8000-000000000003', 'p8-servicio@prueba.invalid',
        'Administrador de prueba P8', 'hash-de-prueba', 'ADMINISTRADOR', 'ACTIVO', now());
