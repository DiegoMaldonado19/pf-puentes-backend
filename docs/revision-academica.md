# Revisión académica — bandeja y apertura

Fuentes: `organización/P7/02-revision-academica.md`, RN-REV-01/03 y contratos del equipo. Los documentos describen funcionalidades y RN, sin asignar un identificador adicional de historia a este incremento.

| Método | Endpoint                                                      | Permiso                                                          |
| ------ | ------------------------------------------------------------- | ---------------------------------------------------------------- |
| GET    | `/api/v1/revisiones/pendientes?page=0&size=10&estado=ENVIADA` | Catedrático activo; estudiantes de cursos propios                |
| POST   | `/api/v1/inspecciones/{inspeccionId}/revisiones`              | Catedrático activo, distinto del autor; curso propio y matrícula |

El GET admite omitir estado para consultar ENVIADA y EN_REVISION. Devuelve `content`, `number`, `size`, `total_elements`, `total_pages`, sin calificación ni datos del formulario. Cada fila contiene contexto académico y `revision_id` si existe un ciclo activo. El POST no requiere cuerpo y devuelve 201 con `id`, `inspeccion_id`, `revisor_id`, `creado_en`. 400 para filtro inválido; 401 sin sesión; 403 por rol/cuenta; 404 para inspección ajena, propia o inexistente; 409 por estado incompatible o ciclo activo.

Pruebas focalizadas:

```bash
./mvnw -B spotless:check -Dtest=RevisionServiceTest,RevisionIntegracionTest test
```

Usan la infraestructura Testcontainers, no la base de desarrollo. La limpieza elimina auditoría, observaciones, transiciones y ciclos antes de inspecciones, formulario y fixtures académicos. No elimina filas ajenas.

Pendiente: veredictos y publicación, evidencia, observaciones ancladas, calificación privada, historial, pendientes del estudiante y alertas. [ADR 0048](adr/0048-bandeja-y-apertura-de-revision-academica.md).

## Validación local — 27/10/2026

14 pruebas focalizadas aprobadas: nueve unitarias de servicio y cinco integraciones con PostgreSQL/PostGIS y MinIO reales en Testcontainers. Incluyen bloqueo de aperturas concurrentes, permisos por fila y reversión de ciclo/transición ante fallo de auditoría. Spotless y PMD aprobados. No certifica `verify` completo ni cobertura global del repositorio.
