# Revisión académica — bandeja y apertura

Fuentes: `organización/P7/02-revision-academica.md`, RN-REV-01/03 y contratos del equipo. Los documentos describen funcionalidades y RN, sin asignar un identificador adicional de historia a este incremento.

| Método | Endpoint                                                      | Permiso                                                          |
| ------ | ------------------------------------------------------------- | ---------------------------------------------------------------- |
| GET    | `/api/v1/revisiones/pendientes?page=0&size=10&estado=ENVIADA` | Catedrático activo; estudiantes de cursos propios                |
| POST   | `/api/v1/inspecciones/{inspeccionId}/revisiones`              | Catedrático activo, distinto del autor; curso propio y matrícula |

El GET admite omitir estado para consultar ENVIADA y EN_REVISION. Devuelve `content`, `number`, `size`, `total_elements`, `total_pages`, sin calificación ni datos del formulario. Cada fila contiene contexto académico y `revision_id` si existe un ciclo activo. El POST no requiere cuerpo y devuelve 201 con `id`, `inspeccion_id`, `revisor_id`, `creado_en`. 400 para filtro inválido; 401 sin sesión; 403 por rol/cuenta; 404 para inspección ajena, propia o inexistente; 409 por estado incompatible o ciclo activo.

## Compatibilidad tras integrar Foro

El listener de Foro participa en la publicación de Inspecciones y crea un hilo por inspección publicada. La limpieza de fixtures de Revisión elimina únicamente los comentarios/hilos vinculados a sus inspecciones, en orden de FK, antes de borrarlas. La prueba de aprobación verifica un único hilo público incluso después de rechazar un segundo veredicto. No se desactiva el listener ni se elimina contenido de otros fixtures.

Comprobación de migraciones: 40 archivos, sin versiones repetidas; las migraciones existentes en `origin/develop` y `origin/main` no fueron alteradas ni eliminadas. `V2026_10_27_1200__crear_aviso_revision.sql` sigue siendo una adición propia de la rama, no una modificación de un esquema ya publicado.

Pruebas focalizadas:

```bash
./mvnw -B spotless:check '-Dtest=*Revision*Test,SaneadorDeAuditoriaTest,DiccionarioDeDatosTest' test pmd:check
```

Usan la infraestructura Testcontainers, no la base de desarrollo. La limpieza elimina auditoría, observaciones, transiciones y ciclos antes de inspecciones, formulario y fixtures académicos. No elimina filas ajenas.

Implementación inicial: [ADR 0055](adr/0055-bandeja-y-apertura-de-revision-academica.md).

## Veredictos y observaciones ancladas

| Método   | Ruta bajo `/api/v1`                      | Función                                                                                 |
| -------- | ---------------------------------------- | --------------------------------------------------------------------------------------- |
| GET      | `/revisiones/{revisionId}`               | Ciclo propio, formulario original, respuestas, campos y observaciones; sin calificación |
| POST     | `/revisiones/{revisionId}/veredicto`     | Emite APROBADA, CAMBIOS_SOLICITADOS o RECHAZADA                                         |
| GET/POST | `/revisiones/{revisionId}/observaciones` | Lista o agrega observaciones del ciclo                                                  |

Veredicto: `{veredicto, observacion, estado_confirmado?, justificacion_estado?}`. Cambios/rechazo exigen observación no vacía. Aprobar llama a `publicarPorAprobacion`, que calcula el IC, publica y emite el evento oficial. No se modifica directamente el estado. Si faltan pesos o falla la publicación, el veredicto y su fecha también se revierten. Cambios usa `emitirVeredicto` de Inspecciones y su retorno a BORRADOR; rechazo es terminal.

Observación: `{elemento_ref, texto}`. El servicio valida la referencia contra `properties` del esquema de la versión original, usando `x-elemento-ref` cuando existe y la ruta real en otro caso. Rechaza referencias ambiguas/inexistentes y texto vacío. Los arreglos se anclan como campo completo, sin inventar índices por fila. Se conserva `resuelta_en = NULL`; la consulta/resolución del estudiante será un incremento posterior.

Las escrituras bloquean primero la inspección y luego cargan el ciclo, verifican responsable actual y revisor asignado; un ciclo cerrado no admite otro veredicto ni nuevas observaciones. Errores: 400 por cuerpo inválido, 404 por revisión ajena, 409 por ciclo/estado incompatible y 422 por referencia/observación/justificación o cálculo inválidos. Se reutiliza auditoría de revisión y observación, en la misma transacción.

Decisión de veredictos: [ADR 0056](adr/0056-veredictos-y-observaciones-ancladas.md).

## Seguimiento, nota privada y avisos

| Método | Ruta bajo `/api/v1`              | Acceso                                                             |
| ------ | -------------------------------- | ------------------------------------------------------------------ |
| GET    | `/revisiones/mis-pendientes`     | Estudiante: observaciones emitidas propias, pendientes             |
| GET    | `/revisiones/mis-inspecciones`   | Estudiante: inspecciones propias con historial, aun sin pendientes |
| PATCH  | `/observaciones/{id}/resolucion` | Autor; exige BORRADOR para la primera resolución                   |
| GET    | `/inspecciones/{id}/revisiones`  | Historial paginado autorizado; jamás contiene calificación         |
| GET    | `/revisiones/{id}/calificacion`  | Solo autor y revisor del ciclo                                     |
| PUT    | `/revisiones/{id}/calificacion`  | Revisor asignado y responsable actual del curso                    |

PUT acepta `{calificacion: 87.50}` o null para retirarla; rango 0–100 y hasta dos decimales. No modifica veredictos anteriores. La nota se omite también de la bitácora general. Resolver es idempotente y preserva texto/ciclo/fecha; no cambia las respuestas ni envía el borrador. Las colecciones usan `page`/`size`, máximo 100, y respuesta `content`, `number`, `size`, `total_elements`, `total_pages`. El historial conserva los ciclos en orden descendente; las observaciones añaden `resuelta_en`.

`AvisoRevisionJob` corre diariamente a las 06:30 GT con ShedLock. A los 15 días naturales exactos desde el envío, ENVIADA o EN_REVISION sin decisión final generan una notificación al responsable activo. La tabla técnica de deduplicación es nueva: `V2026_10_27_1200__crear_aviso_revision.sql`. La marca y la notificación son atómicas, una por envío/destinatario. No configura ni envía correo por su cuenta. [ADR 0057](adr/0057-seguimiento-calificacion-privada-y-avisos-de-revision.md).

Pendiente de integración visual real: fotos/documentos firmados y edición/reenvío en la pantalla de Inspecciones. Capturas del manual y recorrido de navegador no se declaran completados por las pruebas automatizadas.

## Validación local — 27/10/2026

51 pruebas focalizadas aprobadas: 33 de revisión, 17 del saneador de auditoría y una de diccionario. Incluyen integración PostgreSQL/PostGIS y MinIO reales, permisos por fila, conservación de ciclos, privacidad de nota (incluida bitácora), resolución idempotente, límite de 15 días, deduplicación y rollback al fallar Notificaciones. El puerto de cálculo se controla en las pruebas de decisión y Notificaciones se simula solo en el caso de fallo; los recorridos normales del aviso usan su persistencia real.

Spotless y PMD aprobados; diccionario regenerado con la tabla nueva. Reporte local JaCoCo: RevisionService 100 %, DecisionRevisionService 90.8 %, SeguimientoRevisionService 96.3 % y AvisoRevisionService 100 % de líneas. Es el informe local acumulado, no una ejecución `clean verify` ni certificación de calibración o cobertura global.
