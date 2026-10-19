# 0015. Auditoría por aspecto en la capa de servicio

- Estado: aceptado
- Fecha: 11/10/2026
- Directriz o regla relacionada: RN-USR-09, DT-BD-12, DT-SEC-12, DT-BE-10

## Contexto
RN-USR-09 pide registrar toda acción sobre datos con quién, cuándo, qué y los valores anterior y posterior. Si cada módulo escribiera su propia bitácora, el formato divergiría y habría acciones sin registrar. DT-BD-12 prohíbe los triggers: no conocen al usuario de la sesión, ni el motivo, ni la IP.

## Decisión
- Un aspecto (`common/auditoria/AspectoAuditoria`) intercepta los métodos de servicio marcados con `@Auditable(accion, entidad, entidadId, motivo)`.
- **Valores anterior y posterior:** el aspecto lee la fila de la entidad antes y después del método, con el mismo `EntityManager`, y la reduce a un mapa `columna → valor` (las asociaciones quedan como el id del otro lado). El id sale de una expresión SpEL sobre los parámetros (`#id`) o el retorno (`#result.id`).
- **Misma transacción:** el aspecto corre en una transacción `REQUIRED` (se une a la del servicio o abre la suya). La operación y su fila se confirman juntas; si el método falla no queda fila, y si falla la bitácora se revierte el método y el cliente recibe un 500 sin detalles.
- **Datos sensibles (DT-SEC-12):** una lista de exclusión central (`SaneadorDeAuditoria`) omite cualquier campo cuyo nombre contenga `contrasena`, `password`, `hash`, `token` o `secret`, y enmascara los correos (`a***@dominio.com`). Ante la duda, omite.
- **Catálogo y formato:** `Acciones.CATALOGO` es la lista oficial. Al arrancar, `ValidadorDeAuditables` rechaza una acción fuera del catálogo o del formato `MAYUSCULAS_Y_GUION_BAJO`, una entidad que no sea snake_case de una entidad JPA existente y una expresión SpEL inválida. Un módulo que necesite otra acción la agrega al catálogo y avisa al equipo.
- **Contexto de la fila:** el usuario sale de `Authentication.getName()` (contrato 14); sin sesión es una acción del Sistema (`usuario_id` nulo). La IP es `getRemoteAddr()`, que Tomcat resuelve desde `X-Forwarded-For` (`server.forward-headers-strategy=native`), nunca la cabecera cruda. La correlación es un id por petición HTTP; fuera de HTTP, la llamada auditable más externa define el de las que dispara.

## Consecuencias
- Un módulo audita con una anotación; no escribe en `auditoria` ni inyecta su repositorio.
- El método auditable debe cambiar la entidad con JPA y ser llamado a través del proxy de Spring (no desde otro método del mismo bean), como cualquier `@Transactional`.
- Los intentos de inicio de sesión fallidos no se anotan (evita inundar la bitácora); solo el bloqueo de la cuenta lleva `BLOQUEAR_CUENTA` (RN-USR-07).
- `@Auditable.condicion` (SpEL booleana sobre los parámetros y `#result`, evaluada después del método) permite auditar solo algunas ejecuciones: el registro de un intento fallido de inicio de sesión audita únicamente el que bloquea la cuenta. Si la condición da falso, el método se confirma sin fila.
- Una sentencia `UPDATE` masiva de JPQL debe limpiar el contexto de persistencia (`clearAutomatically`), porque el aspecto lee las fotos con el `EntityManager`.
- Cada método auditable agrega dos lecturas por clave primaria y un `INSERT`.
- Un campo cuyo nombre contenga `hash` se omite aunque no sea una contraseña (por ejemplo, el hash de un archivo); es el precio de una exclusión simple y segura.
