# Revisión académica — bandeja y apertura

Fuentes: `organización/P7/02-revision-academica.md`, RN-REV-01/03 y contratos del equipo. Los documentos describen funcionalidades y RN, sin asignar un identificador adicional de historia a este incremento.

| Método | Endpoint                                                      | Permiso                                                          |
| ------ | ------------------------------------------------------------- | ---------------------------------------------------------------- |
| GET    | `/api/v1/revisiones/pendientes?page=0&size=10&estado=ENVIADA` | Catedrático activo; estudiantes de cursos propios                |
| POST   | `/api/v1/inspecciones/{inspeccionId}/revisiones`              | Catedrático activo, distinto del autor; curso propio y matrícula |

El GET admite omitir estado para consultar ENVIADA y EN_REVISION. Devuelve `content`, `number`, `size`, `total_elements`, `total_pages`, sin calificación ni datos del formulario. Cada fila contiene contexto académico y `revision_id` si existe un ciclo activo. El POST no requiere cuerpo y devuelve 201 con `id`, `inspeccion_id`, `revisor_id`, `creado_en`. 400 para filtro inválido; 401 sin sesión; 403 por rol/cuenta; 404 para inspección ajena, propia o inexistente; 409 por estado incompatible o ciclo activo.

Pruebas focalizadas:

```bash
./mvnw -B spotless:check -Dtest=RevisionServiceTest,RevisionIntegracionTest,DecisionRevisionIntegracionTest,CamposRevisionTest test pmd:check
```

Usan la infraestructura Testcontainers, no la base de desarrollo. La limpieza elimina auditoría, observaciones, transiciones y ciclos antes de inspecciones, formulario y fixtures académicos. No elimina filas ajenas.

Implementación inicial: [ADR 0048](adr/0048-bandeja-y-apertura-de-revision-academica.md).

## Veredictos y observaciones ancladas

| Método   | Ruta bajo `/api/v1`                      | Función                                                                                 |
| -------- | ---------------------------------------- | --------------------------------------------------------------------------------------- |
| GET      | `/revisiones/{revisionId}`               | Ciclo propio, formulario original, respuestas, campos y observaciones; sin calificación |
| POST     | `/revisiones/{revisionId}/veredicto`     | Emite APROBADA, CAMBIOS_SOLICITADOS o RECHAZADA                                         |
| GET/POST | `/revisiones/{revisionId}/observaciones` | Lista o agrega observaciones del ciclo                                                  |

Veredicto: `{veredicto, observacion, estado_confirmado?, justificacion_estado?}`. Cambios/rechazo exigen observación no vacía. Aprobar llama a `publicarPorAprobacion`, que calcula el IC, publica y emite el evento oficial. No se modifica directamente el estado. Si faltan pesos o falla la publicación, el veredicto y su fecha también se revierten. Cambios usa `emitirVeredicto` de Inspecciones y su retorno a BORRADOR; rechazo es terminal.

Observación: `{elemento_ref, texto}`. El servicio valida la referencia contra `properties` del esquema de la versión original, usando `x-elemento-ref` cuando existe y la ruta real en otro caso. Rechaza referencias ambiguas/inexistentes y texto vacío. Los arreglos se anclan como campo completo, sin inventar índices por fila. Se conserva `resuelta_en = NULL`; la consulta/resolución del estudiante será un incremento posterior.

Las escrituras bloquean primero la inspección y luego cargan el ciclo, verifican responsable actual y revisor asignado; un ciclo cerrado no admite otro veredicto ni nuevas observaciones. Errores: 400 por cuerpo inválido, 404 por revisión ajena, 409 por ciclo/estado incompatible y 422 por referencia/observación/justificación o cálculo inválidos. Se reutiliza auditoría de revisión y observación, en la misma transacción.

Pendiente: archivos y URL firmadas, calificación privada, historial navegable, pendientes/resolución del estudiante y alertas. [ADR 0049](adr/0049-veredictos-y-observaciones-ancladas.md).

## Validación local — 27/10/2026

23 pruebas focalizadas aprobadas: nueve unitarias de apertura, tres del extractor de campos y once integraciones con PostgreSQL/PostGIS y MinIO reales en Testcontainers. Incluyen permisos por fila, concurrencia de apertura, cambios/rechazo, referencia válida/inválida, publicación con IC y rollback por cálculo fallido. Solo el puerto de cálculo se simula en las seis pruebas de decisión; la publicación, las transiciones y la persistencia son reales. Spotless y PMD aprobados. No certifica `verify` completo ni calibración/cobertura global del repositorio.
