# 0053. Replay autorizado de la baja lógica de borrador

- Estado: aceptado (diseño delegado por S2; GREEN local y verificación independiente aprobados)
- Fecha: 27/10/2026
- Directriz o regla relacionada: DT-OFF-07, DT-BD-11; G7.2 M08; S1–S3 de `odd/tasks/g7-2-completo.md`.

## Contexto

`DELETE /api/v1/inspecciones/{id}` da de baja un borrador propio y devuelve 204. La fila permanece, pero la autorización ordinaria la excluye tras la baja: repetir hoy devuelve 404. Una clave no concede acceso ni permite repetir auditoría o eliminar otro borrador. No hay efectos MinIO en esta operación.

## Decisión

Integrar M08 con `IdempotenciaService` G7.1, huella v1 propia (método DELETE y ruta con UUID), espera explícita de 2 segundos y único resultado admitido 204 con cero bytes y sin Content-Type. Autorizar al actor actual contra la fila original, incluso eliminada, antes y después de consultar el resultado; solo el callback de una operación nueva exige fila no eliminada y estado BORRADOR.

La fila lógica existente es la evidencia de identidad y autor actual. Usar `InspeccionRepository.findById`, una variante acotada del contrato de propiedad y `EscrituraBorradorInspeccion.eliminar` mediante su proxy auditado. No crear otro manifiesto, migración, política de retención ni purga. Resultado, baja, versión y auditoría comparten la transacción del núcleo; conservar el control optimista existente frente a escritores rivales.

## Consecuencias

Un replay confirmado puede devolver 204 aunque la fila siga eliminada o haya cambiado de estado; no cambia ningún dato. Otra clave no revive la operación y conserva 404. La pérdida de titularidad conserva 404 y el rol no admitido 403, sin revelar el resultado. No se amplían las allowlists de PUT/envío ni sus permisos de replay.

La carrera optimista conserva el tratamiento actual del fallo de versión (500 no cacheable); el reintento reevalúa la situación vigente. El bloqueo por clave no equivale a un bloqueo por recurso. La verificación independiente debe cubrir autorización tras baja y carreras antes de cerrar T4. El contrato detallado y los límites de evidencia están en [la guía M08](../idempotencia-baja-borrador.md).

## Evidencia de esta etapa

RED observado por el padre: job1 exit1 en 39 s, 72 pruebas, 15 fallos, cero errores/omisiones. M08: 18 ejecuciones/14 fallos; OpenAPI: un fallo; informes Surefire: borrador 25/25 y envío 22/22. El candidato integra núcleo, huella y autorización específica, sin modificar los helpers históricos. Se añadieron cuatro ejecuciones para claves distintas/sin clave y pérdida de autor durante espera; la carrera de envío ahora ejecuta el POST HTTP completo con fixture publicable. Primer GREEN intentado por el padre: job2 exit1/40 s, 76 pruebas/un fallo/cero errores u omisiones. Único fallo: instantánea JDBC previa al commit del envío no incluía su notificación JPA pendiente (`NotificacionService.save`, sin flush). Primera corrección solo de prueba: flush de la transacción rival antes del esperado, incremento explícito de una notificación y conservación de la comparación íntegra posterior al rollback de DELETE. No se elimina el contador ni se cambia producción. Recheck del padre job3 exit0/207 s: 76/76 focalizadas y 1781/1781 globales sin fallos/errores/omisiones; Spotless apply/check, empaquetado, PMD, tres gates JaCoCo y comprobaciones de diccionario sin cambios/diff aprobados.

Diferencial job4 exit0/54 s contra 3148e3e: Maven base/candidato exit0, 38 observaciones iguales, cinco cambios solicitados y cero regresiones. Comparación estructural JSON con exclusión solo de relojes de servidor; 204 vacío sin Content-Type y replay contra su ACK comprobados RAW. stdout/stderr de Maven conservados, sin afirmar igualdad byte a byte de logs. Artefacto: `odd/scratch/g7-2-t4/ejecucion-20261027T170235266258Z/comparacion.json`.

Verificador independiente `mvqxplxy-4-skic`: S1–S3 cumplidos para T4, sin bloqueantes; job5 exit0/30 s, 25/25 (22 pruebas M08 y tres sondas), cero fallos/errores/omisiones. Las sondas verificaron filas completas de ocho tablas, identidad original tras transferencia y aislamiento usuario/clave. ASSESS comunicado: nueve archivos staged/893 líneas, nativeRisk medium, reviewDue false/under_budget y escalación item2 que requiere verificación independiente; no hubo START ni consumo de autoridad nativa. La unidad conserva comportamiento, contrato y prueba HTTP determinista juntos; no se separan las pruebas de la garantía para ajustar un objetivo de 400 líneas. El dictamen permite cerrar el corte T4; no constituye aprobación nativa, cierre T5 ni aceptación integral.
