# 0036. Recordatorios de programación rutinaria anual

- Fecha: 21/10/2026
- Estado: implementado; verificación registrada en README y manual técnico
- Trazabilidad: RN-MTO-07, DT-BE-14, HU-P8-MTO-08; ADR 0035

## Contexto y decisión

El enunciado exige periodicidad máxima anual y procesos @Scheduled con ShedLock. El usuario aprobó los detalles no expresos: avisar a Catedráticos y Administradores activos cuando falte programación en plazo, incluso antes de vencer, una vez por puente, destinatario y período. La referencia ya confirmada por el grupo es la última ejecución RUTINARIO o, sin ella, el registro del puente. La política de avisos corresponde a aprobación del usuario, no a una regla literal del enunciado.

`RecordatoriosRutinariosJob` revisa diariamente a las 06:15 America/Guatemala, con reloj inyectable y ShedLock. Reutiliza el detector por cursor y lotes de 100 del resumen anual. Una rutina PROGRAMADA/EN_EJECUCION fechada entre hoy y el límite inclusive evita el aviso. Propuestas, fechas ausentes, pasadas o posteriores al límite no lo evitan.

Por puente, el servicio bloquea la fila, revalida referencia/programación y usa el emisor real de P9. El tipo es `RUTINARIO_SIN_PROGRAMAR`, con código, nombre, fecha límite y enlace `/puentes/{id}`. Una marca única por puente, usuario y fecha de referencia se confirma en la misma transacción que las notificaciones. Leerlas o perder nuevamente una programación durante el mismo período no repite avisos; una nueva fecha de ejecución rutinaria abre otro período. Un destinatario activado posteriormente puede recibir su propio aviso.

Un fallo revierte ese puente, permite continuar con otros y se reintenta en la revisión siguiente. No hay endpoint manual del job ni actor ficticio. No se crean, aceptan ni reprograman órdenes: el recordatorio no garantiza por sí solo cumplimiento de RN-MTO-07. La creación automática de propuestas conserva su decisión pendiente. La presentación de notificaciones corresponde a P9 y su API existente entrega estos avisos.

## Persistencia y verificación documental

Migración aditiva `V2026_10_21_1800`, con FK, unicidad y comentarios de tabla/columnas. No se alteran migraciones aplicadas.

El PR anterior falló porque faltó versionar el resultado de `DiccionarioDeDatosTest` después de crear `aviso_inspeccion_vencida`. Este incremento incluye el diccionario generado desde PostgreSQL tanto para aquella tabla como para `aviso_rutinario_anual`. El workflow y su comprobación `git diff --exit-code -- docs/diccionario-de-datos.md` se conservan. Al verificar en una copia temporal se debe trasladar el artefacto generado al repositorio y comprobar que otra generación lo deja idéntico; aprobar Maven por sí solo no verifica ese paso de CI.

Las pruebas usan PostgreSQL/PostGIS, MinIO, notificador P9 y JWT reales: límites, referencia sin ejecuciones, revalidación, roles activos, lectura sin reenvío, nuevo período, destinatarios tardíos, rollback/reintento, concurrencia, unicidad, lotes mayores de cien, aislamiento de fallos y bloqueo distribuido. Los resultados finales y límites de integración se registran en el manual técnico.
