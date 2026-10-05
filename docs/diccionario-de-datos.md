# Diccionario de datos

> Generado por `DiccionarioDeDatosTest` desde los `COMMENT ON` de las migraciones: no se edita a mano. Se actualiza al correr `./mvnw verify`.

- [`archivo`](#archivo): Fotos y documentos; el contenido está en MinIO (DT-BE-13, DT-ALM-01)
- [`asignacion`](#asignacion): Puente que un estudiante puede inspeccionar dentro de su curso (RN-INS-14)
- [`auditoria`](#auditoria): Bitácora de toda acción sobre datos (RN-USR-09); sin contraseñas, tokens ni datos personales completos (DT-SEC-12)
- [`comentario`](#comentario): Comentario de un hilo; un solo nivel de respuesta (RN-FOR-04)
- [`copia_conflicto`](#copia_conflicto): Versión del servidor que pisó un cliente al sincronizar (RN-OFF-09)
- [`curso`](#curso): Curso de Puentes de un ciclo, dirigido por un Catedrático
- [`departamento`](#departamento): Departamentos de Guatemala según el INE
- [`formulario_version`](#formulario_version): Versiones del formulario SIECA como JSON Schema (RN-FRM-01); publicada, es inmutable (RN-FRM-03)
- [`hilo`](#hilo): Hilo del foro permanente de un puente (RN-FOR-01)
- [`idempotencia`](#idempotencia): Respuestas guardadas por Idempotency-Key (DT-OFF-07)
- [`inscripcion`](#inscripcion): Estudiante vinculado a un curso; activarlo y vincularlo es RN-USR-02
- [`inspeccion`](#inspeccion): Inspección de un puente: columnas tipadas + daños en JSONB (DT-BD-02)
- [`invitacion`](#invitacion): Invitaciones del Administrador para roles que no se auto-registran (RN-USR-03)
- [`municipio`](#municipio): Municipios de Guatemala según el INE
- [`notificacion`](#notificacion): Notificaciones de la campana (contrato 5)
- [`observacion_anclada`](#observacion_anclada): Observación del revisor anclada a un campo; genera un pendiente (RN-REV-05)
- [`orden_mantenimiento`](#orden_mantenimiento): Orden de mantenimiento con su ciclo de vida (RN-MTO-04); nunca se borra (RN-MTO-09)
- [`peso_elemento`](#peso_elemento): Peso de cada elemento en la fórmula del IC (RN-IC-02)
- [`puente`](#puente): Inventario permanente: un registro por puente, nunca por visita (RN-INV-01)
- [`recalculo_ic`](#recalculo_ic): IC de una inspección publicada recalculado con otros pesos (RN-IC-08)
- [`revision`](#revision): Ciclo de revisión de una inspección de estudiante (RN-REV-01)
- [`shedlock`](#shedlock): Candados de los procesos programados; la escribe solo ShedLock (DT-BE-14, ADR 0014)
- [`solicitud_puente`](#solicitud_puente): Solicitudes de alta de un Catedrático, pendientes de un Administrador (RN-INV-07)
- [`token_renovacion`](#token_renovacion): Tokens de renovación emitidos, para poder revocarlos (DT-SEC-01)
- [`usuario`](#usuario): Cuentas del sistema; nunca se borran, se desactivan (RN-USR-08)
- [`version_pesos`](#version_pesos): Juego de pesos del IC para una versión del formulario (RN-IC-03)

## archivo

Fotos y documentos; el contenido está en MinIO (DT-BE-13, DT-ALM-01)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 generado en el cliente (DT-OFF-05) |
| `inspeccion_id` | uuid | Sí |  | FK → inspeccion | Inspección a la que pertenece (RN-ARC-01) |
| `orden_mantenimiento_id` | uuid | Sí |  | FK → orden_mantenimiento | Orden de la que es evidencia (RN-MTO-05) |
| `tipo` | character varying(4) | No |  |  | Detectado por firma binaria, no por la extensión (DT-SEC-08) |
| `clave` | character varying(300) | No |  |  | Clave del objeto: {año}/{mes}/{puente_id}/{inspeccion_id u orden_mantenimiento_id}/{uuid}.{ext} (DT-ALM-02) |
| `clave_miniatura` | character varying(300) | Sí |  |  | Clave de la miniatura de 300 px (DT-ALM-04, ADR 0009) |
| `tamano` | bigint | No |  |  | Tamaño en bytes; un PDF hasta 20 MB (RN-ARC-04) |
| `elemento_ref` | character varying(200) | Sí |  |  | Elemento del formulario al que se ancla la foto (RN-ARC-01) |
| `ubicacion` | geography(Point,4326) | Sí |  |  | Dónde se tomó (RN-ARC-03) |
| `capturada_en` | timestamp with time zone | Sí |  |  | Cuándo se tomó (RN-ARC-03) |
| `purgado_en` | timestamp with time zone | Sí |  |  | Cuándo se borró el objeto por la purga de 210 días; el registro queda (RN-ARC-06) |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha de subida, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Última modificación, en UTC |

**Restricciones**

- `UNIQUE (clave)`
- `UNIQUE (clave_miniatura)`
- `CHECK ((num_nonnulls(inspeccion_id, orden_mantenimiento_id) = 1))`
- `CHECK ((((tipo)::text = 'PDF'::text) = (clave_miniatura IS NULL)))`
- `CHECK ((((tipo)::text <> 'PDF'::text) OR (tamano <= 20971520)))`
- `CHECK ((tamano > 0))`
- `CHECK (((tipo)::text = ANY ((ARRAY['JPEG'::character varying, 'WEBP'::character varying, 'PDF'::character varying])::text[])))`

## asignacion

Puente que un estudiante puede inspeccionar dentro de su curso (RN-INS-14)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `inscripcion_id` | uuid | No |  | FK → inscripcion | Inscripción del estudiante en el curso |
| `puente_id` | uuid | No |  | FK → puente | Puente asignado |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha de asignación, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Última modificación, en UTC |

**Restricciones**

- `UNIQUE (inscripcion_id, puente_id)`

## auditoria

Bitácora de toda acción sobre datos (RN-USR-09); sin contraseñas, tokens ni datos personales completos (DT-SEC-12)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `usuario_id` | uuid | Sí |  | FK → usuario | Quién lo hizo; NULL si fue el Sistema (ACT-06) |
| `accion` | character varying(30) | No |  |  | Crear, modificar, cambiar de estado, moderar… |
| `entidad` | character varying(50) | No |  |  | Tabla afectada |
| `entidad_id` | uuid | Sí |  |  | Id de la fila afectada |
| `valor_anterior` | jsonb | Sí |  |  | Valores antes de la acción |
| `valor_nuevo` | jsonb | Sí |  |  | Valores después de la acción |
| `creado_en` | timestamp with time zone | No | now() |  | Momento de la acción, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Igual a creado_en: la bitácora no se modifica |

## comentario

Comentario de un hilo; un solo nivel de respuesta (RN-FOR-04)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `hilo_id` | uuid | No |  | FK → hilo | Hilo |
| `autor_id` | uuid | No |  | FK → usuario | Autor |
| `respuesta_a_id` | uuid | Sí |  | FK → comentario | Comentario al que responde; ese no puede ser a su vez una respuesta |
| `elemento_ref` | character varying(200) | Sí |  |  | Campo de la inspección al que se ancla (RN-FOR-02) |
| `texto` | text | No |  |  | Texto; editable 15 minutos (RN-FOR-05) |
| `editado_en` | timestamp with time zone | Sí |  |  | Última edición |
| `eliminado_en` | timestamp with time zone | Sí |  |  | Lo borró su autor; se muestra la marca "comentario eliminado" (RN-FOR-05) |
| `oculto_en` | timestamp with time zone | Sí |  |  | Lo ocultó la moderación (RN-FOR-08) |
| `motivo_ocultamiento` | text | Sí |  |  | Motivo obligatorio del ocultamiento |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha de publicación, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Última modificación, en UTC |

**Restricciones**

- `CHECK ((respuesta_a_id <> id))`
- `CHECK (((oculto_en IS NULL) = (motivo_ocultamiento IS NULL)))`

## copia_conflicto

Versión del servidor que pisó un cliente al sincronizar (RN-OFF-09)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `inspeccion_id` | uuid | No |  | FK → inspeccion | Inspección en conflicto |
| `datos` | jsonb | No |  |  | Datos que tenía el servidor antes de aplicar los del cliente |
| `creado_en` | timestamp with time zone | No | now() |  | Momento del conflicto, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Igual a creado_en: la copia no cambia |

## curso

Curso de Puentes de un ciclo, dirigido por un Catedrático

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `nombre` | character varying(150) | No |  |  | Nombre o sección del curso |
| `anio` | integer | No |  |  | Año del ciclo |
| `semestre` | integer | No |  |  | Semestre del ciclo: 1 o 2 |
| `catedratico_id` | uuid | No |  | FK → usuario | Catedrático que lo dirige (DT-SEC-04) |
| `vigente` | boolean | No | true |  | TRUE mientras el ciclo esté en curso (RN-INS-14) |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha de creación, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Última modificación, en UTC |

**Restricciones**

- `CHECK (((anio >= 2020) AND (anio <= 2100)))`
- `CHECK ((semestre = ANY (ARRAY[1, 2])))`

## departamento

Departamentos de Guatemala según el INE

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `codigo` | character varying(2) | No |  | PK | Código INE de dos dígitos |
| `nombre` | character varying(60) | No |  |  | Nombre oficial |

**Restricciones**

- `UNIQUE (nombre)`
- `CHECK (((codigo)::text ~ '^[0-9]{2}$'::text))`

## formulario_version

Versiones del formulario SIECA como JSON Schema (RN-FRM-01); publicada, es inmutable (RN-FRM-03)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `codigo` | character varying(20) | No |  |  | Código de la versión que guarda cada inspección (RN-FRM-02) |
| `esquema` | jsonb | No |  |  | JSON Schema con los metadatos del motor (contrato 1) |
| `mapeo` | jsonb | Sí |  |  | Mapeo de campos respecto de la versión anterior (RN-FRM-06); NULL en la primera |
| `estado` | character varying(10) | No | 'BORRADOR'::character varying |  | BORRADOR mientras se edita; ACTIVA la que usan las inspecciones nuevas; RETIRADA solo para consultar |
| `publicada_en` | timestamp with time zone | Sí |  |  | Cuándo se publicó |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha de creación, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Última modificación, en UTC |

**Restricciones**

- `UNIQUE (codigo)`
- `CHECK ((((estado)::text = 'BORRADOR'::text) OR (publicada_en IS NOT NULL)))`
- `CHECK (((estado)::text = ANY ((ARRAY['BORRADOR'::character varying, 'ACTIVA'::character varying, 'RETIRADA'::character varying])::text[])))`

## hilo

Hilo del foro permanente de un puente (RN-FOR-01)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `puente_id` | uuid | No |  | FK → puente | Puente del foro |
| `inspeccion_id` | uuid | Sí |  | FK → inspeccion | Inspección publicada cuyo hilo público es este (RN-FOR-03) |
| `autor_id` | uuid | Sí |  | FK → usuario | Quién lo abrió; NULL si lo abrió el Sistema al publicar |
| `titulo` | character varying(200) | No |  |  | Título |
| `categoria` | character varying(25) | No |  |  | Una de las cinco categorías (RN-FOR-06) |
| `cerrado_en` | timestamp with time zone | Sí |  |  | Cuándo se cerró por moderación |
| `motivo_cierre` | text | Sí |  |  | Motivo obligatorio del cierre (RN-FOR-08) |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha de creación, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Última modificación, en UTC |

**Restricciones**

- `UNIQUE (inspeccion_id)`
- `CHECK (((categoria)::text = ANY ((ARRAY['DISCUSION_TECNICA'::character varying, 'PROPUESTA_INTERVENCION'::character varying, 'CONSULTA'::character varying, 'REFERENCIA_DOCUMENTAL'::character varying, 'ALERTA'::character varying])::text[])))`
- `CHECK (((cerrado_en IS NULL) = (motivo_cierre IS NULL)))`

## idempotencia

Respuestas guardadas por Idempotency-Key (DT-OFF-07)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `usuario_id` | uuid | No |  | FK → usuario | Usuario que mandó la operación |
| `clave` | character varying(100) | No |  |  | Valor del encabezado Idempotency-Key |
| `estado_http` | integer | No |  |  | Código HTTP de la respuesta original |
| `respuesta` | jsonb | Sí |  |  | Cuerpo de la respuesta original |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha de la operación original, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Igual a creado_en: la respuesta guardada no cambia |

**Restricciones**

- `UNIQUE (usuario_id, clave)`
- `CHECK (((estado_http >= 100) AND (estado_http <= 599)))`

## inscripcion

Estudiante vinculado a un curso; activarlo y vincularlo es RN-USR-02

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `curso_id` | uuid | No |  | FK → curso | Curso |
| `estudiante_id` | uuid | No |  | FK → usuario | Estudiante inscrito |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha de inscripción, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Última modificación, en UTC |

**Restricciones**

- `UNIQUE (curso_id, estudiante_id)`

## inspeccion

Inspección de un puente: columnas tipadas + daños en JSONB (DT-BD-02)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 generado en el cliente, antes de cualquier contacto con el servidor (DT-OFF-05) |
| `puente_id` | uuid | No |  | FK → puente | Puente inspeccionado |
| `autor_id` | uuid | No |  | FK → usuario | Autor único y responsable (RN-INS-02) |
| `formulario_version_id` | uuid | No |  | FK → formulario_version | Versión del formulario con la que se levantó (RN-FRM-02) |
| `supersede_a_id` | uuid | Sí |  | FK → inspeccion | Inspección publicada que esta corrige (RN-INS-08) |
| `estado` | character varying(20) | No | 'BORRADOR'::character varying |  | Estado de la máquina de RN-INS-04 |
| `fecha_inspeccion` | date | No |  |  | Ni futura ni anterior al año de construcción del puente (RN-INS-03) |
| `datos` | jsonb | No | '{}'::jsonb |  | Respuestas del formulario, validadas contra el JSON Schema de su versión (RN-FRM-08) |
| `ubicacion_inicio` | geography(Point,4326) | Sí |  |  | GPS del dispositivo al iniciar; se compara con el puente (RN-INS-12) |
| `dispositivo_id` | character varying(100) | Sí |  |  | Identificador del dispositivo (RN-INS-13) |
| `enviada_en` | timestamp with time zone | Sí |  |  | Fecha y hora de envío a revisión (RN-INS-13) |
| `sincronizada_en` | timestamp with time zone | Sí |  |  | Fecha y hora de sincronización (RN-INS-13) |
| `publicada_en` | timestamp with time zone | Sí |  |  | Fecha y hora de publicación |
| `version_pesos_id` | uuid | Sí |  | FK → version_pesos | Pesos con los que se calculó el IC (RN-IC-08) |
| `indice_condicion` | numeric(5,2) | Sí |  |  | IC de 0 a 100, calculado al publicar (RN-IC-01, RN-IC-02) |
| `estado_calculado` | character varying(10) | Sí |  |  | Estado que sale del IC y de las anulaciones (RN-IC-04, RN-IC-05) |
| `anulacion` | character varying(20) | Sí |  |  | Condición de RN-IC-05 que forzó Malo; NULL si ninguna |
| `estado_confirmado` | character varying(10) | Sí |  |  | Estado que confirma el evaluador; es el que toma el puente (RN-IC-06, pendiente #12) |
| `justificacion_estado` | text | Sí |  |  | Obligatoria si el estado confirmado difiere del calculado |
| `eliminado_en` | timestamp with time zone | Sí |  |  | Borrado lógico de un borrador (RN-INS-15, pendiente #6) |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha y hora de creación (RN-INS-13), en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Última modificación, en UTC |

**Restricciones**

- `CHECK (((anulacion)::text = ANY ((ARRAY['SOCAVACION'::character varying, 'ASENTAMIENTO'::character varying, 'PERDIDA_SECCION'::character varying, 'AUSENCIA_PORTANTE'::character varying])::text[])))`
- `CHECK ((supersede_a_id <> id))`
- `CHECK ((((estado)::text = 'PUBLICADA'::text) = ((indice_condicion IS NOT NULL) AND (estado_calculado IS NOT NULL) AND (estado_confirmado IS NOT NULL) AND (publicada_en IS NOT NULL))))`
- `CHECK (((estado_confirmado IS NULL) OR ((estado_confirmado)::text = (estado_calculado)::text) OR (justificacion_estado IS NOT NULL)))`
- `CHECK ((((estado)::text <> 'PUBLICADA'::text) OR (eliminado_en IS NULL)))`
- `CHECK (((estado_calculado)::text = ANY ((ARRAY['BUENO'::character varying, 'REGULAR'::character varying, 'MALO'::character varying])::text[])))`
- `CHECK (((estado)::text = ANY ((ARRAY['BORRADOR'::character varying, 'ENVIADA'::character varying, 'EN_REVISION'::character varying, 'CAMBIOS_SOLICITADOS'::character varying, 'PUBLICADA'::character varying, 'RECHAZADA'::character varying])::text[])))`
- `CHECK (((estado_confirmado)::text = ANY ((ARRAY['BUENO'::character varying, 'REGULAR'::character varying, 'MALO'::character varying])::text[])))`
- `CHECK (((indice_condicion >= (0)::numeric) AND (indice_condicion <= (100)::numeric)))`

## invitacion

Invitaciones del Administrador para roles que no se auto-registran (RN-USR-03)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `correo` | character varying(254) | No |  |  | Correo invitado |
| `rol` | character varying(30) | No |  |  | Rol que tendrá la cuenta |
| `token_hash` | character varying(64) | No |  |  | SHA-256 del token del enlace de invitación |
| `expira_en` | timestamp with time zone | No |  |  | Vencimiento del enlace |
| `aceptada_en` | timestamp with time zone | Sí |  |  | Cuándo se creó la cuenta; NULL si sigue pendiente |
| `administrador_id` | uuid | No |  | FK → usuario | Administrador que invitó |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha de envío, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Última modificación, en UTC |

**Restricciones**

- `UNIQUE (token_hash)`
- `CHECK (((rol)::text = ANY ((ARRAY['PROFESIONAL_EXTERNO'::character varying, 'CATEDRATICO'::character varying, 'ADMINISTRADOR'::character varying])::text[])))`

## municipio

Municipios de Guatemala según el INE

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `codigo` | character varying(4) | No |  | PK | Código INE de cuatro dígitos; los dos primeros son el departamento |
| `departamento_codigo` | character varying(2) | No |  | FK → departamento | Departamento al que pertenece |
| `nombre` | character varying(80) | No |  |  | Nombre oficial |

**Restricciones**

- `UNIQUE (departamento_codigo, nombre)`
- `CHECK (("left"((codigo)::text, 2) = (departamento_codigo)::text))`
- `CHECK (((codigo)::text ~ '^[0-9]{4}$'::text))`

## notificacion

Notificaciones de la campana (contrato 5)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `usuario_id` | uuid | No |  | FK → usuario | Destinatario |
| `tipo` | character varying(50) | No |  |  | Origen: mención, alerta de puente Malo, revisión pendiente… |
| `mensaje` | text | No |  |  | Texto que ve el usuario |
| `enlace` | character varying(300) | Sí |  |  | Ruta de la app a la que lleva |
| `leida_en` | timestamp with time zone | Sí |  |  | Cuándo la leyó; NULL si no la ha leído |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha de creación, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Última modificación, en UTC |

## observacion_anclada

Observación del revisor anclada a un campo; genera un pendiente (RN-REV-05)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `revision_id` | uuid | No |  | FK → revision | Ciclo de revisión |
| `elemento_ref` | character varying(200) | No |  |  | Ruta del campo observado (contrato 2) |
| `texto` | text | No |  |  | Observación |
| `resuelta_en` | timestamp with time zone | Sí |  |  | Cuándo el autor la marcó como resuelta |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha de creación, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Última modificación, en UTC |

## orden_mantenimiento

Orden de mantenimiento con su ciclo de vida (RN-MTO-04); nunca se borra (RN-MTO-09)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `puente_id` | uuid | No |  | FK → puente | Puente (RN-MTO-01) |
| `inspeccion_id` | uuid | Sí |  | FK → inspeccion | Inspección que la originó, si la hay (RN-MTO-01) |
| `tipo` | character varying(15) | No |  |  | Tipología del manual (RN-MTO-03) |
| `estado` | character varying(15) | No | 'PROPUESTA'::character varying |  | Estado de la máquina de RN-MTO-04 |
| `descripcion` | text | No |  |  | Intervención a realizar |
| `elemento_ref` | character varying(200) | Sí |  |  | Elemento afectado (contrato 2) |
| `prioridad_sugerida` | character varying(5) | Sí |  |  | Sugerida por el IC y el peso del elemento (RN-MTO-06, pendiente #13) |
| `prioridad` | character varying(5) | No |  |  | Prioridad vigente |
| `justificacion_prioridad` | text | Sí |  |  | Obligatoria si la prioridad difiere de la sugerida |
| `fecha_programada` | date | Sí |  |  | Fecha programada |
| `fecha_ejecucion` | date | Sí |  |  | Fecha de ejecución (RN-MTO-05) |
| `responsable` | character varying(150) | Sí |  |  | Responsable de la ejecución (RN-MTO-05) |
| `motivo` | text | Sí |  |  | Motivo de descarte o cancelación (RN-MTO-09) |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha de creación, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Última modificación, en UTC |

**Restricciones**

- `CHECK (((prioridad_sugerida IS NULL) OR ((prioridad)::text = (prioridad_sugerida)::text) OR (justificacion_prioridad IS NOT NULL)))`
- `CHECK ((((estado)::text <> 'EJECUTADA'::text) OR ((fecha_ejecucion IS NOT NULL) AND (responsable IS NOT NULL))))`
- `CHECK ((((estado)::text <> ALL ((ARRAY['DESCARTADA'::character varying, 'CANCELADA'::character varying])::text[])) OR (motivo IS NOT NULL)))`
- `CHECK (((estado)::text = ANY ((ARRAY['PROPUESTA'::character varying, 'PROGRAMADA'::character varying, 'EN_EJECUCION'::character varying, 'EJECUTADA'::character varying, 'DESCARTADA'::character varying, 'CANCELADA'::character varying])::text[])))`
- `CHECK (((prioridad)::text = ANY ((ARRAY['ALTA'::character varying, 'MEDIA'::character varying, 'BAJA'::character varying])::text[])))`
- `CHECK (((prioridad_sugerida)::text = ANY ((ARRAY['ALTA'::character varying, 'MEDIA'::character varying, 'BAJA'::character varying])::text[])))`
- `CHECK (((tipo)::text = ANY ((ARRAY['RUTINARIO'::character varying, 'PREVENTIVO'::character varying, 'CORRECTIVO'::character varying, 'EMERGENCIA'::character varying])::text[])))`

## peso_elemento

Peso de cada elemento en la fórmula del IC (RN-IC-02)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `version_pesos_id` | uuid | No |  | FK → version_pesos | Juego de pesos al que pertenece |
| `elemento_ref` | character varying(200) | No |  |  | Ruta del elemento en el formulario (contrato 2) |
| `peso` | numeric(6,3) | No |  |  | Criticidad estructural del elemento |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha de creación, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Última modificación, en UTC |

**Restricciones**

- `UNIQUE (version_pesos_id, elemento_ref)`
- `CHECK ((peso > (0)::numeric))`

## puente

Inventario permanente: un registro por puente, nunca por visita (RN-INV-01)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `codigo` | character varying(20) | No |  |  | GT-<DEPTO>-<MUNI>-<CORRELATIVO>, único e inmutable (RN-INV-02, RN-INV-03) |
| `nombre` | character varying(200) | No |  |  | Nombre del puente |
| `municipio_codigo` | character varying(4) | No |  | FK → municipio | Municipio INE; el departamento sale del municipio |
| `ruta` | character varying(100) | No |  |  | Ruta o carretera |
| `ubicacion` | geography(Point,4326) | No |  |  | Punto WGS84 (EPSG:4326); la UTM se calcula al mostrar (RN-INV-05) |
| `anio_construccion` | integer | Sí |  |  | Año de construcción; la inspección no puede ser anterior (RN-INS-03) |
| `activo` | boolean | No | true |  | FALSE = baja lógica (RN-INV-08) |
| `motivo_baja` | text | Sí |  |  | Motivo de la baja: demolido, sustituido, duplicado… |
| `estado_actual` | character varying(10) | Sí |  |  | Derivado de la última inspección publicada; NULL = Sin evaluar (RN-INV-10) |
| `indice_condicion_actual` | numeric(5,2) | Sí |  |  | IC de la última inspección publicada (RN-INV-09) |
| `fecha_ultima_inspeccion` | date | Sí |  |  | Fecha de la última inspección publicada (RN-INV-09) |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha de alta, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Última modificación, en UTC |

**Restricciones**

- `UNIQUE (codigo)`
- `CHECK (((anio_construccion >= 1800) AND (anio_construccion <= 2100)))`
- `CHECK ((activo OR (motivo_baja IS NOT NULL)))`
- `CHECK ((num_nulls(estado_actual, indice_condicion_actual, fecha_ultima_inspeccion) = ANY (ARRAY[0, 3])))`
- `CHECK (((codigo)::text ~ '^GT-[0-9]{2}-[0-9]{2,4}-[0-9]{4}$'::text))`
- `CHECK (((estado_actual)::text = ANY ((ARRAY['BUENO'::character varying, 'REGULAR'::character varying, 'MALO'::character varying])::text[])))`
- `CHECK (((indice_condicion_actual >= (0)::numeric) AND (indice_condicion_actual <= (100)::numeric)))`

## recalculo_ic

IC de una inspección publicada recalculado con otros pesos (RN-IC-08)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `inspeccion_id` | uuid | No |  | FK → inspeccion | Inspección publicada |
| `version_pesos_id` | uuid | No |  | FK → version_pesos | Pesos usados en el recálculo |
| `indice_condicion` | numeric(5,2) | No |  |  | IC recalculado |
| `estado_calculado` | character varying(10) | No |  |  | Estado recalculado |
| `anulacion` | character varying(20) | Sí |  |  | Anulación aplicada; NULL si ninguna |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha del recálculo, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Igual a creado_en: un recálculo no se modifica |

**Restricciones**

- `UNIQUE (inspeccion_id, version_pesos_id)`
- `CHECK (((anulacion)::text = ANY ((ARRAY['SOCAVACION'::character varying, 'ASENTAMIENTO'::character varying, 'PERDIDA_SECCION'::character varying, 'AUSENCIA_PORTANTE'::character varying])::text[])))`
- `CHECK (((estado_calculado)::text = ANY ((ARRAY['BUENO'::character varying, 'REGULAR'::character varying, 'MALO'::character varying])::text[])))`
- `CHECK (((indice_condicion >= (0)::numeric) AND (indice_condicion <= (100)::numeric)))`

## revision

Ciclo de revisión de una inspección de estudiante (RN-REV-01)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `inspeccion_id` | uuid | No |  | FK → inspeccion | Inspección revisada |
| `revisor_id` | uuid | No |  | FK → usuario | Catedrático revisor; nunca el autor (RN-REV-03) |
| `veredicto` | character varying(20) | Sí |  |  | Veredicto emitido; NULL mientras está EN_REVISION (RN-REV-04) |
| `observacion` | text | Sí |  |  | Obligatoria para Cambios solicitados y Rechazada |
| `calificacion` | numeric(5,2) | Sí |  |  | Calificación 0–100, visible solo para autor y revisor (RN-REV-07, pendiente #13) |
| `emitida_en` | timestamp with time zone | Sí |  |  | Cuándo se emitió el veredicto |
| `creado_en` | timestamp with time zone | No | now() |  | Inicio de la revisión, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Última modificación, en UTC |

**Restricciones**

- `CHECK (((calificacion >= (0)::numeric) AND (calificacion <= (100)::numeric)))`
- `CHECK (((veredicto IS NULL) OR ((veredicto)::text = 'APROBADA'::text) OR (observacion IS NOT NULL)))`
- `CHECK (((veredicto IS NULL) = (emitida_en IS NULL)))`
- `CHECK (((veredicto)::text = ANY ((ARRAY['APROBADA'::character varying, 'CAMBIOS_SOLICITADOS'::character varying, 'RECHAZADA'::character varying])::text[])))`

## shedlock

Candados de los procesos programados; la escribe solo ShedLock (DT-BE-14, ADR 0014)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `name` | character varying(64) | No |  | PK | Nombre del proceso, el de @SchedulerLock(name) |
| `lock_until` | timestamp without time zone | No |  |  | Hasta cuándo nadie más puede ejecutarlo, en UTC |
| `locked_at` | timestamp without time zone | No |  |  | Cuándo empezó la última ejecución, en UTC |
| `locked_by` | character varying(255) | No |  |  | Host de la réplica que lo ejecutó |

## solicitud_puente

Solicitudes de alta de un Catedrático, pendientes de un Administrador (RN-INV-07)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `catedratico_id` | uuid | No |  | FK → usuario | Quién solicita |
| `nombre` | character varying(200) | No |  |  | Nombre propuesto |
| `municipio_codigo` | character varying(4) | No |  | FK → municipio | Municipio INE propuesto |
| `ruta` | character varying(100) | No |  |  | Ruta propuesta |
| `ubicacion` | geography(Point,4326) | No |  |  | Punto WGS84 propuesto |
| `estado` | character varying(10) | No | 'PENDIENTE'::character varying |  | PENDIENTE, APROBADA o RECHAZADA |
| `administrador_id` | uuid | Sí |  | FK → usuario | Administrador que la resolvió |
| `puente_id` | uuid | Sí |  | FK → puente | Puente creado al aprobarla |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha de la solicitud, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Última modificación, en UTC |

**Restricciones**

- `CHECK ((((estado)::text <> 'APROBADA'::text) OR (puente_id IS NOT NULL)))`
- `CHECK ((((estado)::text = 'PENDIENTE'::text) OR (administrador_id IS NOT NULL)))`
- `CHECK (((estado)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'APROBADA'::character varying, 'RECHAZADA'::character varying])::text[])))`

## token_renovacion

Tokens de renovación emitidos, para poder revocarlos (DT-SEC-01)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `usuario_id` | uuid | No |  | FK → usuario | Dueño del token |
| `token_hash` | character varying(64) | No |  |  | SHA-256 del token; nunca el valor |
| `expira_en` | timestamp with time zone | No |  |  | Vence a los 7 días |
| `revocado_en` | timestamp with time zone | Sí |  |  | Cuándo se revocó; NULL si sigue vigente |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha de emisión, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Última modificación, en UTC |

**Restricciones**

- `UNIQUE (token_hash)`

## usuario

Cuentas del sistema; nunca se borran, se desactivan (RN-USR-08)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `correo` | character varying(254) | No |  |  | Correo de acceso; único sin distinguir mayúsculas |
| `nombre` | character varying(150) | No |  |  | Nombre completo |
| `contrasena_hash` | character varying(100) | No |  |  | Hash BCrypt, factor de trabajo 12 o más (RN-USR-06) |
| `rol` | character varying(30) | No |  |  | Rol global único (RN-USR-05) |
| `estado` | character varying(20) | No | 'PENDIENTE'::character varying |  | PENDIENTE hasta que lo activen (RN-USR-02); INACTIVO es la baja lógica |
| `correo_verificado_en` | timestamp with time zone | Sí |  |  | Cuándo verificó su correo (RN-USR-01) |
| `numero_colegiado` | character varying(20) | Sí |  |  | Número de colegiado del Profesional externo |
| `colegiado_verificado_en` | timestamp with time zone | Sí |  |  | Cuándo el Administrador verificó el colegiado (RN-USR-04) |
| `intentos_fallidos` | integer | No | 0 |  | Intentos fallidos de inicio de sesión en la ventana actual (RN-USR-07) |
| `primer_fallo_en` | timestamp with time zone | Sí |  |  | Inicio de la ventana de 15 minutos de intentos fallidos |
| `bloqueado_hasta` | timestamp with time zone | Sí |  |  | Fin del bloqueo temporal |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha de creación, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Última modificación, en UTC |

**Restricciones**

- `CHECK ((((rol)::text <> 'PROFESIONAL_EXTERNO'::text) OR (numero_colegiado IS NOT NULL)))`
- `CHECK ((((rol)::text <> 'PROFESIONAL_EXTERNO'::text) OR ((estado)::text <> 'ACTIVO'::text) OR (colegiado_verificado_en IS NOT NULL)))`
- `CHECK (((estado)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'ACTIVO'::character varying, 'INACTIVO'::character varying])::text[])))`
- `CHECK ((intentos_fallidos >= 0))`
- `CHECK (((rol)::text = ANY ((ARRAY['ESTUDIANTE'::character varying, 'PROFESIONAL_EXTERNO'::character varying, 'CATEDRATICO'::character varying, 'ADMINISTRADOR'::character varying])::text[])))`

## version_pesos

Juego de pesos del IC para una versión del formulario (RN-IC-03)

| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |
|---|---|---|---|---|---|
| `id` | uuid | No |  | PK | UUID v7 |
| `formulario_version_id` | uuid | No |  | FK → formulario_version | Versión del formulario a la que aplica |
| `acta` | character varying(300) | Sí |  |  | Referencia al acta de calibración firmada; NULL = pesos provisionales sin acta (pendiente #11) |
| `vigente_desde` | timestamp with time zone | No | now() |  | Desde cuándo se usa para calcular el IC |
| `creado_en` | timestamp with time zone | No | now() |  | Fecha de creación, en UTC |
| `actualizado_en` | timestamp with time zone | No | now() |  | Última modificación, en UTC |
