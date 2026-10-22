# 0034. Alertas de inspección vencida sin avisos duplicados

- Estado: implementado; revisión/entrega y presentación frontend P9 pendientes
- Fecha: 21/10/2026
- Trazabilidad: HU-P8-MTO-09, RN-MTO-08, DT-BE-14, DT-BD-05/07/08/10/11; contrato 5 de P9

## Política adoptada por el usuario

RN-MTO-08 exige alertar cuando un puente no registre inspección publicada en más de doce meses. No detalla destinatarios, fecha base, nunca inspeccionados ni repetición. Tras explicar esas cuatro dudas, el usuario autorizó continuar con:

1. Destinatarios: Catedráticos y Administradores activos.
2. Base: fecha de publicación más reciente entre inspecciones PUBLICADA, no fecha de visita ni campos derivados del puente.
3. Sin publicadas: fecha de registro del puente.
4. Un aviso por puente y destinatario durante el período vencido; una nueva publicación reinicia el plazo y habilita otro aviso cuando vuelva a vencer.

Son decisiones adoptadas por el usuario, no respuestas atribuidas al grupo ni requisitos literales adicionales. La revisión es diaria. Se suman doce meses de calendario a la referencia en UTC y se exige vencimiento estrictamente anterior a la evaluación. El límite exacto no avisa; no se reemplaza por 365 días ni por restar un año a hoy (diferencia importante en años bisiestos). Borradores, enviadas, en revisión, cambios solicitados y rechazadas no reinician el plazo.

## Implementación

`AlertasInspeccionJob` corre diariamente a las 06:00 de America/Guatemala con @Scheduled/@SchedulerLock y el proveedor JDBC de P1, usando hora de base para el candado. Toma una sola hora de evaluación con Clock UTC para toda la corrida y recorre puentes por ID en lotes de 100. El repositorio obtiene la última publicación mediante LATERAL y un índice parcial por puente/fecha/ID; no carga relaciones JPA LAZY ni colecciones globales sin límite.

`AlertasInspeccionService.alertar` es una operación Java interna del job, no una API pública o una acción del usuario. Cada puente se procesa en una transacción: bloquea su fila, vuelve a comprobar referencia/vencimiento y obtiene destinatarios activos aún no avisados en lotes de 100. Si una publicación ya cambió la referencia antes de comprobarla, no envía el aviso vencido anterior. Al integrar P5 se debe respetar la consistencia de publicación/inventario; este proceso no publica inspecciones ni sustituye su transacción.

Cada destinatario recibe `NotificacionService.notificar` de P9, tipo `INSPECCION_VENCIDA`, mensaje con código/nombre del puente y enlace `/puentes/{id}` a la ficha existente de P3. No hay autenticación propia, correo adicional ni una pantalla duplicada de notificaciones en P8. La API existente de P9 entrega el aviso únicamente a su destinatario autenticado; su DTO usa `contenido`, no el contrato de páginas de órdenes.

## Duplicados, persistencia y fallos

La migración aditiva crea `aviso_inspeccion_vencida` con UUID v7, puente, destinatario, inspección opcional, referencia y marcas UTC. La restricción `UNIQUE NULLS NOT DISTINCT (puente_id, usuario_id, inspeccion_id)` deduplica también puentes nunca inspeccionados; PostgreSQL 16+ ya es requisito del proyecto. Las claves foráneas y comentarios forman parte del esquema/diccionario. No se modifican migraciones fusionadas.

La notificación P9 y la marca se confirman o revierten juntas en la misma base/transacción. No hay doble escritura a sistemas externos ni una cola/outbox nueva. Si falla un envío, se revierte ese puente completo y el job registra el fallo, continúa con los demás y reintenta en la siguiente corrida diaria. El bloqueo por puente serializa llamadas concurrentes incluso fuera del planificador; ShedLock evita ejecuciones simultáneas entre réplicas del job. Las marcas no se borran al leer el aviso y no alteran inspecciones, IC, órdenes ni auditoría de decisiones del usuario.

Sin destinatarios no se crea marca. Un destinatario que se active o adquiera el rol después puede recibir su único aviso del período en una revisión posterior; los ya avisados no lo repiten. El criterio de esta entrega recorre el inventario permanente sin agregar una política nueva de exclusión por baja de puente. La garantía de mantenimiento rutinario anual es otra historia (MTO-08) y sigue pendiente.

## Verificación y límites

`AlertasInspeccionTest` prueba PostgreSQL/PostGIS, migración real y P9: límite exacto y un microsegundo posterior, año bisiesto, registro sin publicadas, visita distinta de publicación, selección de última publicación, otros estados, nuevas publicaciones, lectura sin reenvío, cuentas/roles, destinatarios activados después, ausencia de destinatarios, rollback/reintento, concurrencia, lotes de más de cien puentes/destinatarios, aislamiento de fallos y candado ShedLock real. También consulta la alerta mediante JWT real y GET de P9 y verifica que un estudiante ajeno no la recibe.

Resultados globales y prueba de uso: [manual técnico P8](../manual-tecnico-p8.md). La publicación real P5 sigue pendiente, pero la consulta trabaja con sus columnas/estado persistidos existentes. No se crea un emisor P5 ficticio ni se atribuye a P8 la presentación frontend de P9. MTO-09 permanece en progreso hasta integración del recorrido y revisión/entrega/CI.

Verificación final: **1089 pruebas backend** sin fallos/errores/omisiones (17 nuevas), compilación limpia, Spotless, PMD, empaquetado y tres reglas JaCoCo aprobados. Servicios: 95.16 % de líneas; AlertasInspeccionService: 100 % líneas/ramas; núcleo IC/máquina mantiene 100 % según clases configuradas. Frontend sin cambios; última verificación previa: 366 pruebas. No se acredita CI remoto.
