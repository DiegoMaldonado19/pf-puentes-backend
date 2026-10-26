# 0044 — Estimación preliminar del IC y confirmación de estado con justificación

- Estado: implementado
- Fecha: 25/10/2026
- Trazabilidad: HU-P8-IC-05, HU-P8-IC-06, RN-IC-06, RN-IC-07, RN-INS-05, RN-INS-07, DT-ARQ-06

## Contexto y decisiones

1. **Estimación preliminar (RN-IC-07, DT-ARQ-06):**
   - El Índice de Condición (IC) oficial solo existe y tiene validez para inspecciones en estado `PUBLICADA`. La base de datos garantiza esto mediante el constraint:
     `CHECK ((estado = 'PUBLICADA') = (indice_condicion IS NOT NULL AND estado_calculado IS NOT NULL AND estado_confirmado IS NOT NULL AND publicada_en IS NOT NULL))`
   - Los borradores o revisiones intermedias pueden requerir una estimación del IC, pero esta debe estar rotulada explícitamente como "IC PRELIMINAR", no debe persistirse como resultado oficial, no altera el inventario de puentes ni su condición vigente en el mapa, y respeta la privacidad de las inspecciones no publicadas (RN-INS-05).
   - Se implementan dos endpoints bajo `/api/v1`:
     - `POST /api/v1/formularios/{formularioId}/ic-preliminar`: calcula on-the-fly a partir de respuestas sin persistencia. Si no se indica `version_pesos_id`, toma automáticamente el juego más reciente según `vigente_desde` e `id`.
     - `POST /api/v1/inspecciones/{inspeccionId}/ic-preliminar`: calcula para borradores e inspecciones no publicadas leyendo sus respuestas vía SQL parametrizado (`JdbcTemplate`, ADR 0010) sin modificar `inspeccion` ni `puente`. Si la inspección ya está publicada, rechaza con `409 Conflict` remitiendo a la consulta oficial de recálculos (`RecalculoIcController`).

2. **Confirmación de estado con justificación (RN-IC-06):**
   - El evaluador puede modificar el estado de condición respecto al cálculo automático, pero debe sustentarlo mediante una justificación por escrito no vacía.
   - El modelo de dominio `ConfirmacionEstadoCondicion` en `com.cunoc.puentes.indice` valida inmutablemente:
     - Si `estadoConfirmado != estadoCalculado`, exige `justificacionEstado` no nula y no blanca (con limpieza de espacios en blanco).
     - Si `estadoConfirmado == estadoCalculado`, la justificación es opcional y se normaliza a `null` si viene vacía.
     - Conserva tanto `estadoCalculado` como `estadoConfirmado`.
     - Expone `tieneDiscrepancia()`, `esElevacionDeAnulacionCritica()` (alerta si se pretende subir a Regular o Bueno un estado forzado a Malo por RN-IC-05) y `estadoEfectivo()`.
   - Se expone el endpoint `POST /api/v1/inspecciones/confirmacion-estado/validacion` para que las pantallas de captura y revisión de P5 validen y normalicen la justificación antes de someter la inspección a publicación.

## Consecuencias y seguridad

- Roles autorizados: `ESTUDIANTE`, `CATEDRATICO`, `ADMINISTRADOR` y `PROFESIONAL_EXTERNO`.
- No requiere migraciones ni altera el esquema existente.
- Cobertura completa mediante pruebas unitarias en `ConfirmacionEstadoCondicionTest` y pruebas de integración contra PostgreSQL/PostGIS en `EstimacionPreliminarIcTest`.

