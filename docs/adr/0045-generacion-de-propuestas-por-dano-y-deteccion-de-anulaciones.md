# 0045 — Generación de propuestas de mantenimiento por daño, detector de anulaciones críticas P4 y fachada oficial de IC para P5

- Estado: implementado
- Fecha: 25/10/2026
- Trazabilidad: HU-P8-MTO-02, HU-P8-IC-03, HU-P8-IC-04, HU-P8-MTO-08, RN-IC-02, RN-IC-04, RN-IC-05, RN-MTO-01, RN-MTO-02, RN-MTO-06, RN-MTO-07, Contrato 3, Contrato 6

## Contexto y decisiones

1. **Detección de condiciones críticas desde el esquema P4 (`DetectorCondicionesCriticas`, RN-IC-05):**
   - El formulario versión `V1.0.0` de P4 declara 6 campos de daño con marcador `"x-anulacion-critica"` que corresponden a las 4 causas de anulación crítica de `RN-IC-05` (`SOCAVACION`, `ASENTAMIENTO`, `PERDIDA_SECCION`, `AUSENCIA_PORTANTE`).
   - En tanto el grupo confirme o refine umbrales particulares por campo, se adopta como política limpia y configurable que toda calificación entera `>= 3` (`UMBRAL_CRITICO_DEFECTO = 3`) en cualquiera de los campos marcados con `"x-anulacion-critica"` activa la causa correspondiente en `CondicionesCriticas` (`socavacionCritica`, `asentamiento`, `perdidaSeccion`, `ausenciaPortante`), forzando `estadoCalculado = MALO` y conservando el puntaje numérico del IC.
   - `EstimacionPreliminarIcService` utiliza automáticamente `DetectorCondicionesCriticas` cuando la petición no envía banderas manuales explícitas.

2. **Fachada de cálculo oficial del IC para P5 (`CalculoIndiceCondicionService`, Contrato 3):**
   - Expone `calcular(datos, formularioId)` y `calcular(datos, formularioId, versionPesosIdOpcional)` devolviendo `ResultadoCalculoOficialIc` (`indiceCondicion`, `estadoCalculado`, `anulaciones`, `anulacionPersistible`, `versionPesosId`, `calificaciones`).
   - Selecciona el juego de pesos indicado o el más reciente de esa versión de formulario (`vigente_desde DESC, id DESC`), prepara las calificaciones con `PreparacionCalificacionesService`, evalúa anulaciones críticas con `DetectorCondicionesCriticas` y ejecuta `CalculadoraIndiceCondicion`.

3. **Catálogo daño → intervención y prioridad sugerida (`CatalogoIntervencionesDano`, RN-MTO-01, RN-MTO-02, RN-MTO-06):**
   - Al evaluar los elementos de daño de una inspección publicada (escala 0 a 4):
     - Calificación `0` o `1`: no genera propuesta automática.
     - Calificación `2`: genera propuesta de tipo `PREVENTIVO` (mantenimiento preventivo y sellado/protección focalizada).
     - Calificación `3` (sin anulación crítica): genera propuesta de tipo `CORRECTIVO` (reparación estructural focalizada y restitución de capacidad).
     - Calificación `4` o campo con anulación crítica activa (`>= 3` en `x-anulacion-critica`): genera propuesta de tipo `EMERGENCIA` (intervención urgente por daño severo o condición crítica).
   - La `prioridadSugerida` e inicial (`prioridad`) se calcula combinando el IC de la inspección, la calificación del daño y el peso del elemento (`RN-MTO-06`):
     - `ALTA`: si es `EMERGENCIA`, o `IC < 50`, o (`calificación >= 3` y `peso >= 2.0`), o (`IC < 75` y `calificación >= 3`).
     - `MEDIA`: si `50 <= IC < 75`, o `calificación == 3`, o (`calificación == 2` y `peso >= 2.5`).
     - `BAJA`: en el resto de casos (`IC >= 75` con daño moderado de peso estándar).

4. **Generación transaccional, auditada e idempotente (`GeneracionPropuestasMantenimientoService` y `PropuestasInspeccionPublicadaListener`):**
   - `PropuestasInspeccionPublicadaListener` escucha `InspeccionPublicada` (Contrato 6) con `@Transactional(propagation = Propagation.MANDATORY)`.
   - Verifica la existencia del puente (en consonancia con el contrato transaccional de P3); si la inspección publicada está persistida en `inspeccion`, lee sus `datos` y `version_pesos_id`, evalúa `CatalogoIntervencionesDano` y persiste cada orden en estado `PROPUESTA` mediante `CreacionPropuestaOrdenService` (`@Auditable(accion = Acciones.CREAR, entidad = "orden_mantenimiento")`).
   - Garantiza idempotencia verificando `existePorInspeccionYElemento(inspeccionId, elementoRef)` antes de crear.
   - Además, expone `asegurarPropuestaRutinariaAnual(puenteId)` para generar de forma idempotente una propuesta `RUTINARIO` cuando el puente carece de programación rutinaria anual en plazo y no tiene ya una orden rutinaria abierta (`RN-MTO-07`).

## Consecuencias y verificación

- No requiere migraciones adicionales ni altera las tablas existentes.
- Cobertura unitaria e integración en `CatalogoIntervencionesDanoTest`, `DetectorCondicionesCriticasTest` y `GeneracionPropuestasMantenimientoTest`, manteniendo en verde `InspeccionPublicadaListenerTest` y `RecordatoriosRutinariosTest`.

