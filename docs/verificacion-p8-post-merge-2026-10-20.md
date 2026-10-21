# P8 — Verificación posterior al merge de Cursos

Fecha: 20/10/2026, America/Guatemala. Paso 1 del plan autorizado por el usuario.

## Estado verificado

Backend `feat/IC-base`, commit base `1f801da`: integra `origin/develop` `875a391` (P7) y conserva `b3e117d` (GET de propuestas de P8). La verificación se ejecutó sobre esa combinación; los únicos cambios de esta entrega son documentales. No se modificaron fuentes, pruebas, migraciones, dependencias ni workflows.

## Resultado reproducible

Con JDK 25, Docker disponible y PostgreSQL/PostGIS y MinIO reales de `PruebaIntegracion`, desde la raíz del backend:

```sh
JAVA_TOOL_OPTIONS='-Xmx768m -XX:ActiveProcessorCount=2 -Dspring.test.context.cache.maxSize=8' ./mvnw -B -ntp clean spotless:check verify pmd:check
```

`JAVA_HOME` debe apuntar al JDK 25. Los límites anteriores son del proceso de verificación; no cambian la configuración de producción.

| Control | Resultado |
|---|---|
| Compilación limpia de fuentes y pruebas | Aprobada |
| Spotless | Aprobado |
| Suite backend completa | **651 pruebas; 0 fallos, 0 errores, 0 omitidas** |
| Cursos incorporado por el pull | 25 de `CursoIntegracionTest` y 2 de `CursoServiceTest`, incluidas en las 651 |
| JaCoCo: servicios ≥70 % de líneas según la regla del proyecto | Aprobado |
| JaCoCo: núcleo IC, 100 % de líneas y ramas por clase incluida | Aprobado |
| JaCoCo: máquina/entidad de órdenes, 100 % de líneas y ramas por clase incluida | Aprobado |
| PMD y empaquetado | Aprobados; **BUILD SUCCESS** |
| Numeración de ADR | 27 documentos con números únicos |
| Enlaces del ADR renumerado | Actualizados y comprobados localmente |

Reportes regenerables: `target/surefire-reports/`, `target/site/jacoco/index.html` y `target/pmd.xml`. No se incorporan archivos de `target/` al commit.

## Colisión documental resuelta

El ADR de validación de pesos de P8 pasa de 0018 a [0027](adr/0027-validacion-de-configuraciones-de-pesos.md). Solo cambian su número y una nota de trazabilidad; la decisión original se conserva. Se actualizan los enlaces de README, manual técnico y ADR 0019. Cursos conserva [0018](adr/0018-cursos-asignaciones-y-contratos-p7.md), sin editar sus fuentes ni documentos.

## Alcance de la comprobación

Estas pruebas verifican el código efectivamente recibido; no acreditan clases o flujos mencionados únicamente en documentación. En particular, el checkout contiene dos clases de prueba de Cursos; el `CursoSesionIntegracionTest` citado en sus documentos no está presente. La activación productiva de estudiantes sigue siendo un puerto pendiente de P2, no una función entregada por este pull.

No se modificó ni se volvió a probar el frontend: su última verificación independiente continúa en 115 unitarios. Todavía faltan sesión/guardias y ruta protegida. Esta comprobación local no sustituye el CI remoto ni acredita el flujo completo P4 → P5 → IC/propuestas.

No se agregan funcionalidades de P8 ni se cierran HU. La siguiente entrega del plan es historial/detalle de órdenes, después de confirmar sus lectores; Catedrático/Admin para propuestas ya está resuelto y no se consulta de nuevo. Inicio/cierre, evidencia, descarte/ajuste y programación mantienen sus decisiones propias. La estimación de P8 sigue en aproximadamente 34 %.
