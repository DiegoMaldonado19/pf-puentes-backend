# Recorrido real de P8 en navegador

Actualizado 25/10/2026. Complementa las pruebas backend/frontend; no sustituye `verify`, las reglas de cobertura ni la generación de propuestas desde P4/P5.

## Requisitos y ejecución

Los repositorios deben estar como hermanos (`pf-puentes-backend` y `pf-puentes-frontend`). Requiere Java 25, Docker, Node compatible con el frontend y dependencias frontend instaladas (`npm ci`). Instala el navegador desde el frontend con `npx playwright install chromium`. Si ya tienes Google Chrome instalado, puedes usar `P8_CHROME_CHANNEL=chrome` en el comando Maven en lugar de descargar Chromium.

Desde **pf-puentes-backend**:

```bash
JAVA_HOME=/ruta/a/java-25 ./mvnw -B -ntp -Dtest=RecorridoP8Navegador test
```

En esta máquina, se comprobó con Chrome instalado:

```bash
JAVA_HOME=/home/tuxrex/.sdkman/candidates/java/25-open \
JAVA_TOOL_OPTIONS='-Xmx768m -XX:ActiveProcessorCount=2' \
P8_CHROME_CHANNEL=chrome \
./mvnw -B -ntp -Dtest=RecorridoP8Navegador test
```

Para otro checkout frontend, define `P8_FRONTEND_DIR` con su ruta absoluta. No requiere levantar los servicios locales ni iniciar sesión manualmente. El puerto `127.0.0.1:8093` debe estar libre.

El nombre del ejecutor es intencional: se invoca expresamente con `-Dtest=RecorridoP8Navegador`; no coincide con los patrones por defecto de Surefire. Por ello, el CI backend normal compila el ejecutor pero no necesita Node/Chrome. No se agregan casos omitidos ni se desactivan pruebas existentes. Tampoco cambia el workflow compartido.

## Qué ejecuta

El ejecutor hereda `PruebaIntegracion`: crea PostgreSQL/PostGIS y MinIO desechables mediante Testcontainers, arranca Spring Boot con HTTP y actuator en puertos aleatorios y carga fixtures de `src/test/resources`. Configura hashes BCrypt reales para las cuentas ficticias del recorrido. No abre ni modifica la base local del desarrollador ni lee su `.env`. Las órdenes preparadas solo permiten ejercitar las operaciones ya construidas: no acreditan generación automática por daños/publicación.

Playwright compila la aplicación de producción y un servidor Node exclusivo de pruebas sirve la SPA y reenvía `/api` al backend temporal, sin reemplazar sus respuestas. Requiere la marca del ejecutor y rechaza el puerto local habitual 8080. El navegador usa login, cookie de renovación, Bearer, guardias, servicios, transacciones, auditoría y objetos MinIO reales. La opción de cookie sin Secure y el origen de prueba solo se aplican en este contexto de pruebas. Service worker bloqueado para no reutilizar cachés; el harness offline conserva su configuración separada.

Se recorren siete escenarios:

1. Administrador: versión de formulario → juego → elementos; recargar conserva sesión.
2. Administrador: ficha P3 → mantenimiento → aceptar, programar, justificar prioridad, iniciar, rechazar cierre sin foto, comprimir/subir una imagen a MinIO, cerrar, recargar y consultar evidencia; descartar otra propuesta y conservar el motivo.
3. Catedrático: consulta mantenimiento y tiene denegada la administración de pesos.
4. Estudiante: rutas y API de mantenimiento/pesos denegadas.
5. Profesional: las mismas restricciones, con cuenta profesional válida.
6. Administrador: abrir campos de daño, seleccionar V1.0.0 desde P4 y consultar sus 19 referencias canónicas sin pesos configurados; ocultar el panel.
7. Administrador: crear dos juegos completos de 19 pesos, uno con referencia de acta de prueba y otro provisional; comprobar consulta tras recarga y conservación del primero. SQL posterior valida valores, actas, auditoría, esquema e inspecciones anteriores sin cambios.

Al terminar, Java comprueba en SQL estado, fecha, responsable, prioridad, motivo, una evidencia, auditorías y ausencia de órdenes nuevas. Los contenedores desaparecen con la JVM. El icono enviado como imagen es un archivo de prueba; no se presenta como evidencia de un puente real.

## Resultados y límites

Ejecución local del 25/10 aprobada: **7 escenarios Playwright y 1 prueba orquestadora JUnit**, sin fallos ni omisiones. Google Chrome 151 instalado; en la preparación original la descarga del Chromium gestionado por Playwright agotó su tiempo de espera. Se utilizó el canal Chrome soportado por Playwright sin simular un navegador ni respuestas API. Build de producción aprobado dentro del recorrido. Consulta y creación usan los 19 campos de la semilla real de Flyway; los demás escenarios conservan sus fixtures aislados. Los pesos introducidos son exclusivamente de prueba, no calibración académica.

Frontend: `playwright-report/p8/index.html`, `test-results/p8/` y `test-results/p8-ejecucion.log` (ignorados por Git). Backend: `target/surefire-reports`. No se graban trazas de red porque contienen credenciales/tokens; las cuentas son exclusivamente de prueba. El proceso hijo escribe a un archivo para no interferir con el canal de Surefire.

Este recorrido no valida generación automática P4/P5, calibración oficial, recálculos, confirmación humana, cancelación aún no acordada, presentación P9, red degradada, TLS/nginx o despliegue remoto. No usar las credenciales ficticias para poblar un entorno de producción.
