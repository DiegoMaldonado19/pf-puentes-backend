# 0010. Modelo de datos base para todos los módulos

- Estado: aceptado
- Fecha: 04/10/2026
- Directriz o regla relacionada: DT-ARQ-03, DT-BD-02 a DT-BD-11, DT-BE-07, DT-BE-09, DT-OFF-05

## Contexto
Al 04/10 ningún módulo tenía tablas ni entidades, y cada responsable iba a crear las suyas por separado. Con 9 personas en paralelo eso arriesga nombres, tipos y llaves distintos entre módulos.

## Decisión
- Una migración por módulo (`V2026_10_04_2000` a `2014`) con las tablas del enunciado y sus llaves foráneas. También llevan los CHECK de las reglas que la base puede validar y un `COMMENT ON` por columna, del que sale el diccionario de datos.
- Una entidad JPA por tabla, en el paquete de su módulo. Todas extienden `common/EntidadBase`, que da el id UUID v7 de Hibernate, `creado_en` y `actualizado_en`. Hay tres excepciones:
  - `Inspeccion` y `Archivo`, cuyo id lo genera el cliente (DT-OFF-05);
  - los catálogos del INE (ADR 0011).
- Dentro de un módulo, las relaciones son `@ManyToOne(fetch = LAZY)` (DT-BE-09). Entre módulos se guarda solo el UUID: ningún módulo importa entidades de otro.
- El `estado` de `Inspeccion` y de `OrdenMantenimiento` tiene setter de paquete: solo lo cambia la máquina de estados del servicio (DT-BE-07).
- `spring.jpa.hibernate.ddl-auto=validate`: si una entidad no coincide con su tabla, la aplicación no arranca.

## Consecuencias
- Cada responsable parte de su tabla y de su entidad. Los cambios van en migraciones nuevas y aditivas (DT-BD-09); estas no se editan.
- Referenciar por id evita dependencias entre módulos y cargas perezosas accidentales. A cambio, para unir datos de dos módulos hay que hacerlo en la consulta (`JOIN ... ON`) o en el servicio.
- Los daños del formulario viven en `inspeccion.datos`. Las ~50 columnas tipadas de DT-BD-02 se agregan cuando P4 cargue las 8 secciones y se sepa cuáles se consultan.
- Algunas reglas quedan en los servicios porque la base no las puede ver:
  - el revisor no es el autor (RN-REV-03);
  - las respuestas del foro tienen un solo nivel (RN-FOR-04);
  - cerrar una orden exige foto de evidencia (RN-MTO-05).
