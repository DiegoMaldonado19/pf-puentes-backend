# 0043 — Recálculo fechado y comparable del Índice de Condición

- Estado: implementado
- Fecha: 25/10/2026
- Trazabilidad: HU-P8-IC-07, RN-IC-08, RN-INS-07, RN-USR-10, DT-ARQ-03/06, DT-BD-11/12, DT-BE-08/09

## Contexto y decisión

`RN-IC-08` exige que un recálculo del Índice de Condición (IC) bajo otro juego de pesos no altere retroactivamente inspecciones ya publicadas, registrándose como un recálculo fechado y comparable. La tabla `recalculo_ic` dispone de la restricción `UNIQUE (inspeccion_id, version_pesos_id)` y conserva su marca temporal UTC en `creado_en`.

Para avanzar este incremento independiente mientras P5 completa el flujo de publicación e informe:

- **Actores autorizados (política adoptada por el responsable P8):** `CATEDRATICO` y `ADMINISTRADOR` con cuenta activa, validado en controlador (`RecalculoIcController`) y servicios (`RecalculoIcService` y `CreacionRecalculoIcService`).
- **Endpoints HTTP bajo `/api/v1/inspecciones/{inspeccionId}/recalculos-ic`:**
  - `GET`: devuelve `ComparacionRecalculoIcDTO` con el resultado oficial inmutable de la inspección publicada (`ResultadoOriginalIcDTO`) y la página de recálculos (`PaginaPesosDTO<RecalculoIcDTO>`) ordenada por ID descendente (`page` desde 0, `size` 1–100, por defecto 20).
  - `POST`: recibe `SolicitarRecalculoIcDTO` (`version_pesos_id`) y responde `200 OK` con `RecalculoIcDTO`.

## Diseño interno, inmutabilidad e idempotencia

1. **Aislamiento modular (ADR 0010):** `RecalculoIcService` consulta `inspeccion` mediante SQL parametrizado (`JdbcTemplate`), sin importar la entidad JPA de P5.
2. **Solo inspecciones `PUBLICADA`:** si la inspección no existe responde `404` (`inspeccion-no-encontrada`); si su estado es distinto de `PUBLICADA` (`BORRADOR`, `EN_REVISION`, `RECHAZADA`) responde `409` (`inspeccion-no-publicada`).
3. **Bloqueo e idempotencia:** en `POST`, la consulta aplica `FOR UPDATE OF i` sobre la fila de `inspeccion` (sin modificar sus columnas ni marcas temporales) para serializar solicitudes concurrentes. Si ya existe una fila en `recalculo_ic` para `(inspeccion_id, version_pesos_id)`, se devuelve ese `RecalculoIcDTO` existente sin insertar duplicados ni generar una segunda entrada de auditoría.
4. **Cálculo reproducible y conservación de anulaciones:** reutiliza `PreparacionCalificacionesService.preparar(formularioVersionId, versionPesosId, datos)` (validando que el juego exista, pertenezca a la misma versión del formulario y cubra exactamente sus campos de daño) y `CalculadoraIndiceCondicion.calcular(calificaciones, condiciones)`. Se conserva la `anulacion` crítica registrada en la inspección publicada (`SOCAVACION`, `ASENTAMIENTO`, `PERDIDA_SECCION`, `AUSENCIA_PORTANTE` o `null`), forzando `MALO` cuando existe anulación y redondeando el IC a dos decimales (`RoundingMode.HALF_UP`) para `NUMERIC(5, 2)`.
5. **Inmutabilidad y auditoría:** `CreacionRecalculoIcService` persiste la fila nueva con `@Transactional(propagation = Propagation.MANDATORY)` y `@Auditable(accion = Acciones.CREAR, entidad = "recalculo_ic", entidadId = "#result.id")`. No modifica `inspeccion`, no altera `puente` (`estado_actual`, `indice_condicion_actual`, `fecha_ultima_inspeccion`) y no emite `InspeccionPublicada`.

## Verificación

`RecalculoIcTest` verifica con PostgreSQL/PostGIS real el cálculo con un juego nuevo, la conservación intacta de `inspeccion` y `puente`, la idempotencia en reintentos sin duplicar filas ni auditorías, la vista comparativa original vs. recálculos, las cuatro causas de `Anulacion`, el rechazo `409` de estados no publicados, `404/422` por recursos ausentes o juegos incompatibles y la restricción `401/403` por rol en HTTP y servicio.

