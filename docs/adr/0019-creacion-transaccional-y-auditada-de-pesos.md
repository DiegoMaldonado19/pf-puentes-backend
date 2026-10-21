# 0019. Creación transaccional y auditada de pesos

- Estado: propuesto para revisión; servicio implementado con entradas normalizadas de prueba
- Fecha: 18/10/2026
- Historia: HU-P8-IC-04, servicio de creación
- Reglas/directrices: RN-IC-03, RN-USR-09, RN-FRM-03, DT-BD-12, DT-BE-03, DT-SEC-04

## Contexto

Ya existen definiciones validadas y repositorios de pesos ([ADR 0027](0027-validacion-de-configuraciones-de-pesos.md)). Falta guardar un juego completo con autorización, auditoría y rollback de toda la operación. El usuario confirmó el 18/10 que P4 aún no entregó su contrato: no se conocen las rutas ni la representación de metadatos del formulario real.

## Decisión

`ConfiguracionPesosService.crear(JuegoPesos, ElementosDeDano)` es una operación interna de backend, protegida por `@PreAuthorize("hasRole('ADMINISTRADOR')")` y `@Transactional`. Devuelve el UUID del juego creado y no expone edición o eliminación. Siempre crea entidades nuevas, sin sobrescribir juegos anteriores ni modificar el esquema del formulario.

`ElementosDeDano` es una entrada interna inmutable con UUID de versión y referencias permitidas. Su adaptador futuro debe obtenerla de metadatos confiables del servidor. **No se aceptará esa lista desde el cliente HTTP:** permitir que el cliente declare qué campos son daños invalidaría la comprobación. Las pruebas entregan listas ficticias explícitas; no se ha implementado ni supuesto el formato de P4.

Antes de insertar, el servicio comprueba entradas presentes, que ambos UUID de formulario coincidan, que la versión exista y que todas las referencias configuradas pertenezcan a los campos de daño normalizados. Versión inexistente produce `NegocioException` 404; versión incompatible o referencia no permitida, 422. No se exige que el juego incluya todos los campos del conjunto: la política de aplicabilidad/completitud depende del contrato pendiente de P4 y del acuerdo académico.

La existencia del formulario se consulta por SQL en el repositorio propio, sin importar su entidad JPA, siguiendo ADR 0010. No se elige automáticamente el formulario activo ni el juego vigente. Los estados permitidos del formulario para configurar pesos y el vínculo entre nuevas versiones deben acordarse con P4 antes de exponer creación real. Las pruebas usan formularios ficticios en BORRADOR, sin afirmar que esa sea la política final.

El mapeo lo realiza `PesosMapper` de MapStruct. Se conserva la inicialización existente de `vigenteDesde`; no se añade una política de activación ni parámetros de vigencia nuevos. Una referencia de acta se guarda como referencia, sin verificar firma ni declarar calibración aprobada.

El juego se audita con `@Auditable(CREAR, version_pesos)`. Cada peso se crea por otro bean interno, `CreacionPesoService`, con su propio `@Auditable(CREAR, peso_elemento)` y el mismo permiso de Administrador. La separación permite que el aspecto intercepte cada llamada; no se usa autoinvocación ni se escribe directamente en la tabla de auditoría. El aspecto existente comparte correlación y transacción: todos los datos y registros se confirman juntos o se revierten juntos si falla un peso o la bitácora.

## Verificación

Prueba focalizada: `./mvnw -B -ntp -Dtest=ConfiguracionPesosServiceTest,ElementosDeDanoTest test`, con Docker disponible. Usa PostgreSQL real, proxies de seguridad/aspectos y transacciones de negocio; la prueba no abre una transacción que oculte los commits o rollbacks.

Las pruebas verifican creación y auditoría del juego y cada peso con usuario/correlación; creación de un segundo juego conservando el anterior; rechazo de todos los otros roles y ausencia de sesión; entradas ausentes, metadatos incompatibles, formulario inexistente y referencia no permitida. Un fallo simulado al guardar el segundo peso prueba rollback de los datos y de la auditoría ya escrita. Un fallo real de FK al auditar un usuario inexistente también debe revertir todo; es una inyección de fallo de prueba, no un modo de autenticación de producción.

Resultado focalizado del 18/10: 16 casos aprobados, sin fallos, errores ni omisiones. La verificación global se registra al finalizar la entrega en el avance de P8.

Verificación global final del 19/10: `./mvnw -B -ntp spotless:check verify pmd:check` aprobado con 440 casos, incluidos 160 de `indice/` y los 16 nuevos de esta entrega, sin fallos, errores ni omisiones. Los dos servicios nuevos y `ElementosDeDano` alcanzaron 100 % de líneas y ramas donde existen ramas; se cumplieron las reglas JaCoCo del proyecto.

La primera ejecución global agotó las conexiones de PostgreSQL porque cada contexto de pruebas reservaba su propio pool por defecto. Se acotó únicamente `PruebaIntegracion` a máximo cuatro conexiones por contexto y mínimo una en reposo; no se modificó el pool de producción ni se desactivaron pruebas. La repetición completa incluyó los casos concurrentes de P2 y pasó.

## Consecuencias y pendientes

El servicio puede probarse con datos normalizados; no puede anunciarse como administración integrada de pesos reales. Falta el adaptador de P4, acuerdos de versiones y completitud, calibración con acta, DTO/API/OpenAPI y pantalla Admin. No se crea controlador ni ruta de producción o demo para esta entrega. Las pruebas de autorización son sobre el servicio; las futuras rutas requerirán sus propias pruebas 401/403 por rol.

La conservación histórica se garantiza en esta operación de creación por generar juegos nuevos. Los repositorios JPA internos todavía tienen operaciones genéricas de actualización/borrado; otros servicios futuros deben respetar la misma política. No se añade un bloqueo de base de datos contra toda modificación futura ni se declara completada HU-P8-IC-04.
