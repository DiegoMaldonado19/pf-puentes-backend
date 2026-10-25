-- Históricos ficticios para comprobar que crear pesos no recalcula ni reescribe inspecciones.
UPDATE formulario_version SET esquema='{"type":"object","properties":{"a":{"type":"integer","minimum":0,"maximum":4,"x-dano":true,"x-elemento-ref":"elemento.a"},"b":{"type":"integer","minimum":0,"maximum":4,"x-dano":true,"x-elemento-ref":"elemento.b"}}}'::jsonb,
 estado='RETIRADA', publicada_en=now() WHERE id='019a0018-0000-7000-8000-000000000001';
INSERT INTO departamento(codigo,nombre) VALUES ('86','Prueba pesos');
INSERT INTO municipio(codigo,departamento_codigo,nombre) VALUES ('8686','86','Prueba pesos');
INSERT INTO puente(id,codigo,nombre,municipio_codigo,ruta,ubicacion) VALUES
 ('019a0040-0000-7000-8000-000000000001','GT-86-8686-9901','Prueba pesos','8686','Ruta prueba','SRID=4326;POINT(-90.5 14.6)');
INSERT INTO version_pesos(id,formulario_version_id,acta) VALUES
 ('019a0040-0000-7000-8000-000000000011','019a0018-0000-7000-8000-000000000001','Acta anterior');
INSERT INTO peso_elemento(id,version_pesos_id,elemento_ref,peso) VALUES
 ('019a0040-0000-7000-8000-000000000012','019a0040-0000-7000-8000-000000000011','elemento.a',1);
INSERT INTO inspeccion(id,puente_id,autor_id,formulario_version_id,estado,fecha_inspeccion,datos,ubicacion_inicio,dispositivo_id,publicada_en,version_pesos_id,indice_condicion,estado_calculado,estado_confirmado) VALUES
 ('019a0040-0000-7000-8000-000000000021','019a0040-0000-7000-8000-000000000001','019a0018-0000-7000-8000-000000000003','019a0018-0000-7000-8000-000000000001','PUBLICADA','2026-10-01','{}','SRID=4326;POINT(-90.5 14.6)','prueba-pesos',now(),'019a0040-0000-7000-8000-000000000011',80,'BUENO','BUENO');
