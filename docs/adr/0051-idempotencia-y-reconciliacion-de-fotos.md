# 0051. Idempotencia y reconciliación de foto y miniatura

- Estado: decisión técnica delegada por S2; implementación, verificación independiente y diferencial locales; sin aceptación externa.
- Fecha: 27/10/2026
- Reglas: DT-OFF-07, RN-OFF-08, RN-ARC, DT-ALM-03/04.

## Decisión

M05 reutiliza G7.1, el puerto `IntencionIdempotencia`, el orquestador y el journal de [ADR 0050](0050-idempotencia-y-reconciliacion-de-documentos.md). No crea un segundo protocolo de recuperación. La tabla histórica `intento_documento` admite ahora un manifiesto principal y una miniatura opcional; las intenciones PDF existentes conservan MIME PDF y miniatura nula. La migración es aditiva. Metadatos y MIME declarados quedan vinculados mediante la huella; el journal conserva identidades e integridad remota, no blobs ni URLs.

## Contrato M05

- Clave opcional, `strip`, hasta 100 puntos de código Unicode y alcance por usuario. Sin clave/blanco conserva el flujo histórico UUID/201, incluida deduplicación entre tipos confirmados.
- Huella SHA-256 v1 de JSON ordenado: POST, ruta canónica, UUID canónico, `foto` y `miniatura` (SHA-256, tamaño, MIME declarado incluido null), `elemento_ref`, latitud, longitud y `capturada_en`. Coordenadas numéricamente equivalentes se normalizan y el instante se expresa en UTC. Null no equivale a cadena vacía. Nombre cliente, boundary, orden multipart, autenticación, X-Sgp-Sync y parámetros ajenos al DTO no participan.
- Solo 201 JSON exacto; URLs originales incluso vencidas. No cachear binding, permisos, formato, cupo, estado ni transitorios. Otra huella/legado produce 409 `clave-idempotencia-incompatible`.
- Espera G7.1 de 2 s y `lock_timeout` SQL de 2 s, sin deadline global HTTP. Conflicto reintentable `operacion-en-proceso`.
- Autorizar padre propio/no eliminado antes y después del núcleo, también tras espera/replay. Nueva mutación reautoriza bajo bloqueo y exige BORRADOR; replay no exige editabilidad ni fila de archivo vigente.

## Dos objetos, una intención

Bajo inspección→UUID se validan cupo y contenidos con las reglas existentes. Una transacción REQUIRES_NEW fija ambas claves y sus hashes/tamaños/MIME detectados antes del primer PUT. Cada objeto se inspecciona y, solo si está ausente, se guarda y vuelve a inspeccionar. El retry reutiliza íntegros y completa faltantes sin regenerar identidades. Fallo de lectura no equivale a ausencia; contenido incompatible da 409 `objeto-archivo-incompatible` sin sobrescritura.

Finalización del journal, fila de archivo, actividad y resultado se confirman juntos en PostgreSQL. Rollback final conserva la intención pendiente y los objetos. No hay compensación, expiración ni purga nueva. Si se pierde autorización/editabilidad no se finaliza una intención pendiente.

El guard B1 comparte las reservas PDF/foto: otra clave o flujo histórico no crea objetos con UUID reservado. Mantenimiento mantiene orden→UUID y el creador histórico; UUID ya confirmado conserva `yaGuardado` antes de validar nueva creación. PDF mantiene su huella, allowlist y reconciliación de un objeto.

## Evidencia y límites

RED del padre: 12 pruebas M05, ocho fallos, cero errores/omisiones; 19 PDF aprobadas, exit 1/29 s. El rechazo de foto con la misma clave de PDF pendiente obtuvo `operacion-en-proceso`, no la incompatibilidad de huella esperada: faltaba que M05 entrara en G7.1, no era un fixture incorrecto.

Los casos adicionales de autorización, normalización, reserva foto, miniatura remota y OpenAPI se añadieron después de ese RED; no tienen RED separado. Job9: 113/113 pruebas focalizadas y 1725/1725 globales, sin fallos/errores/omisiones; JaCoCo, Spotless/package/PMD y generación del diccionario, exit 0. Verificación independiente: 37/37, tres sondas de recuperación y reserva, 94/94 originales y package, todo exit 0, sin bloqueantes reproducidos. El primer intento GREEN tuvo un fallo de fixture: reemplazar getMultiFileMap no cambiaba la miniatura enviada; se corrigió construyendo multipart nuevo, sin cambiar producción ni expectativas. Diferencial contra e1f7f0d aprobado: ambos Maven exit 0, 105 observaciones base/101 candidato y ninguna diferencia inesperada. Se clasifican los cambios solicitados de clave de 101 caracteres y reutilización PDF→foto de la misma clave, incluidas sus consecuencias de ausencia de objetos/fila. Tres rechazos de binding se comparan por todos sus campos y multiconjunto de errores, no RAW; replay foto/PDF comprobado RAW. El primer harness compartía accidentalmente claves entre operaciones; se corrigió para aislarlas y conservar además el escenario cruzado explícito. Pruebas con PostgreSQL/MinIO reales y autenticación de prueba; fallo incierto simulado después de PUT real, no corte físico ni JWT real. M09/T4 no están implementados por esta decisión.
