# 0021. Núcleo de transiciones de órdenes

- Estado: propuesto para revisión; núcleo implementado, servicio e integración pendientes
- Fecha: 19/10/2026
- Historias: partes de HU-P8-MTO-03/04/05/06
- Reglas/directrices: RN-MTO-04/05/09; DT-BE-07; DT-CAL-01

## Contexto

La entidad base de órdenes existe, pero todavía no hay servicio, repositorio ni API de mantenimiento. Los documentos del equipo mantienen pendientes la matriz de lectura/ejecución, el alcance por fila y el origen de CANCELADA. P2 y P4 aún no entregan sus piezas frontend/metadatos en los checkouts revisados. Se pueden construir las transiciones y condiciones conocidas sin resolver esos acuerdos por suposición.

## Decisión

`EstadoOrden.permite` define las cuatro aristas conocidas: PROPUESTA → PROGRAMADA, PROPUESTA → DESCARTADA, PROGRAMADA → EN_EJECUCION y EN_EJECUCION → EJECUTADA. Las repeticiones, saltos y salidas de estados terminales producen `NegocioException` 409. CANCELADA se conserva para lectura/hidratación, pero **ninguna transición hacia ella se habilita todavía**. Esto expresa una funcionalidad pendiente; no determina que la cancelación esté prohibida definitivamente. El diagrama secundario propone un origen que la copia del enunciado no permite confirmar.

`MaquinaEstadosOrden` valida el grafo y los requisitos antes de mutar. Descartar exige motivo no nulo ni blanco. Cerrar exige fecha, responsable no vacío y al menos una foto guardada; un responsable de más de 150 caracteres se rechaza por la columna existente `VARCHAR(150)`, sin truncarlo. No se inventan restricciones de fechas futuras/pasadas ni campos obligatorios de programación no acordados.

`DatosTransicionOrden` es un registro interno, no un DTO HTTP. Su contador de fotos no puede ser negativo y **deberá obtenerlo el servicio desde `ArchivoService.contarEvidencias(ordenId)`**, después de autorizar la operación y validar la orden y sus archivos. Nunca debe aceptarse un contador declarado por el navegador ni confundirse una foto seleccionada con evidencia guardada. Este núcleo no verifica archivos físicos ni su pertenencia por sí mismo.

La entidad elimina `setEstado`, incluso con visibilidad de paquete. Su método interno `cambiarEstado` es la única puerta de mutación del estado y delega primero a la máquina. Solo después guarda datos de cierre o motivo y cambia el estado. Si falla la validación, no deja modificaciones parciales. Programar e iniciar conservan intervención, prioridades y programación ya presentes. No hay borrado de órdenes ni reapertura de terminales.

El futuro servicio será la única fachada autorizada: resolverá orden, permisos por rol/fila, evidencia real y transacción, y llamará a esta puerta interna bajo auditoría. El núcleo no concede roles ni reemplaza esa fachada. No se añadieron controladores, rutas, DTO HTTP, repositorios, migraciones o frontend en esta entrega.

## Verificación

Los comandos requieren el JDK 25 ya definido por el `pom.xml` y el CI del repositorio; no se cambia esa versión en esta entrega.

60 pruebas unitarias: matriz de los 36 pares de estados, requisitos de cierre/descarte, ausencia de datos, contador negativo, frontera Unicode de responsable, flujo completo, conservación de datos, ausencia de setter y ausencia de cambios parciales ante rechazos. No requieren iniciar backend, Postman ni Docker.

```sh
./mvnw -B -ntp -Dtest=MaquinaEstadosOrdenTest,OrdenMantenimientoTest test
./mvnw -B -ntp jacoco:report jacoco:check@check-maquina-orden
```

La ejecución Maven `check-maquina-orden` exige 100 % de líneas y ramas **por clase** en enum, máquina, datos y entidad, además de conservar las reglas de IC y servicios. La verificación focalizada pasó con 100 % en las cuatro clases. Verificación global del 19/10 aprobada con JDK 25: `./mvnw -B -ntp clean spotless:check verify pmd:check`, 521 casos (60 nuevos de mantenimiento), sin fallos, errores ni omisiones; formato, PMD y las tres reglas JaCoCo cumplidos. Hubo que seleccionar el JDK 25 instalado frente al 21 activo y recompilar desde cero artefactos incompatibles de `target`. No se cambió la versión de Java del proyecto ni se desactivaron pruebas.

## Pendientes

Las cuatro HU quedan en progreso por piezas compartidas; ninguna se cierra con el núcleo. Falta servicio/repositorio con permisos y auditoría, evidencia real con pertenencia a la orden, persistencia/transacciones, acuerdos de programación/cancelación/ejecutor y API/UI. MTO-01/02, generación desde publicación, catálogo y prioridades no se implementan aquí. Las entidades antiguas con CANCELADA siguen siendo representables sin habilitar una transición aún no acordada.
