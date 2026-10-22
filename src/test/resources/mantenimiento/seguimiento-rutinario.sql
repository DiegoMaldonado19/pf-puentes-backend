-- Datos ficticios: no crean catálogo ni órdenes automáticas de producción.
INSERT INTO departamento (codigo,nombre) VALUES ('91','Departamento ficticio');
INSERT INTO municipio (codigo,departamento_codigo,nombre) VALUES ('9191','91','Municipio ficticio');
INSERT INTO puente (id,codigo,nombre,municipio_codigo,ruta,ubicacion,creado_en) VALUES
 ('019a0035-0000-7000-8000-000000000001','GT-91-9191-9901','Puente anual A','9191','Ruta ficticia','SRID=4326;POINT(-90.51 14.62)','2020-01-01T00:00:00Z'),
 ('019a0035-0000-7000-8000-000000000002','GT-91-9191-9902','Puente anual B','9191','Ruta ficticia','SRID=4326;POINT(-90.52 14.63)','2024-03-01T00:30:00Z');
INSERT INTO usuario (id,correo,nombre,contrasena_hash,rol,estado,correo_verificado_en,numero_colegiado,colegiado_verificado_en,colegiado_verificado_por) VALUES
 ('019a0035-0000-7000-8000-000000000003','admin@anual-prueba.com','Admin ficticio','hash-no-utilizable','ADMINISTRADOR','ACTIVO',now(),null,null,null),
 ('019a0035-0000-7000-8000-000000000004','cate@anual-prueba.com','Cate ficticio','hash-no-utilizable','CATEDRATICO','ACTIVO',now(),null,null,null),
 ('019a0035-0000-7000-8000-000000000005','estudiante@anual-prueba.com','Estudiante ficticio','hash-no-utilizable','ESTUDIANTE','ACTIVO',now(),null,null,null),
 ('019a0035-0000-7000-8000-000000000006','profesional@anual-prueba.com','Profesional ficticio','hash-no-utilizable','PROFESIONAL_EXTERNO','ACTIVO',now(),'PRUEBA-P8-35',now(),'019a0035-0000-7000-8000-000000000003');
INSERT INTO orden_mantenimiento (id,puente_id,tipo,estado,descripcion,prioridad,fecha_ejecucion,responsable) VALUES
 ('019a0035-0000-7000-8000-000000000010','019a0035-0000-7000-8000-000000000001','RUTINARIO','EJECUTADA','Rutina ejecutada reciente','MEDIA','2025-03-01','Responsable ficticio'),
 ('019a0035-0000-7000-8000-000000000011','019a0035-0000-7000-8000-000000000001','RUTINARIO','EJECUTADA','Rutina anterior','MEDIA','2025-01-01','Responsable ficticio'),
 ('019a0035-0000-7000-8000-000000000012','019a0035-0000-7000-8000-000000000001','CORRECTIVO','EJECUTADA','Correctivo no reinicia rutina','ALTA','2026-02-27','Responsable ficticio');
INSERT INTO orden_mantenimiento (id,puente_id,tipo,estado,descripcion,prioridad,fecha_programada,motivo) VALUES
 ('019a0035-0000-7000-8000-000000000013','019a0035-0000-7000-8000-000000000001','RUTINARIO','PROGRAMADA','Rutina para hoy','MEDIA','2026-02-28',null),
 ('019a0035-0000-7000-8000-000000000014','019a0035-0000-7000-8000-000000000001','RUTINARIO','EN_EJECUCION','Rutina en fecha límite','MEDIA','2026-03-01',null),
 ('019a0035-0000-7000-8000-000000000015','019a0035-0000-7000-8000-000000000001','RUTINARIO','PROGRAMADA','Fecha programada pasada','MEDIA','2026-02-27',null),
 ('019a0035-0000-7000-8000-000000000016','019a0035-0000-7000-8000-000000000001','RUTINARIO','PROGRAMADA','Posterior al límite','MEDIA','2026-03-02',null),
 ('019a0035-0000-7000-8000-000000000017','019a0035-0000-7000-8000-000000000001','RUTINARIO','PROGRAMADA','Sin fecha','MEDIA',null,null),
 ('019a0035-0000-7000-8000-000000000018','019a0035-0000-7000-8000-000000000001','RUTINARIO','PROPUESTA','Propuesta no es programación','MEDIA','2026-02-28',null),
 ('019a0035-0000-7000-8000-000000000019','019a0035-0000-7000-8000-000000000001','RUTINARIO','CANCELADA','Histórica cancelada','MEDIA','2026-02-28','Motivo ficticio'),
 ('019a0035-0000-7000-8000-000000000020','019a0035-0000-7000-8000-000000000001','RUTINARIO','DESCARTADA','Histórica descartada','MEDIA','2026-02-28','Motivo ficticio'),
 ('019a0035-0000-7000-8000-000000000021','019a0035-0000-7000-8000-000000000001','PREVENTIVO','PROGRAMADA','Otro tipo no cubre rutina','MEDIA','2026-02-28',null);
