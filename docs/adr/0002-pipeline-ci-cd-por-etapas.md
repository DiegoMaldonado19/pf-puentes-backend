# 0002. Pipeline CI/CD por etapas en GitHub Actions

- Estado: aceptado
- Fecha: 03/10/2026
- Directriz o regla relacionada: DT-CAL-05, DT-CAL-06, DT-CAL-07

## Contexto
Cada PR debe compilar, probarse, revisar el formato y pasar el análisis estático. Solo hay dos ramas, `develop` y `main`, y tres entornos a los que desplegar.

## Decisión
El workflow `.github/workflows/ci-cd.yml` tiene cuatro jobs encadenados:

| Job | Qué hace |
|---|---|
| `build` | `spotless:check` + compilación y empaquetado + `pmd:check` (PMD 7 con sus reglas por defecto) |
| `test` | `mvn verify`: JUnit, Testcontainers y la regla de JaCoCo. Sube el reporte como artefacto |
| `push` | Publica o promueve la imagen en Docker Hub ([0003](0003-version-y-promocion-de-imagenes.md)) |
| `deploy` | Por SSH: actualiza el clon del entorno, escribe su `.env` y ejecuta `docker compose up --wait` |

| Evento | Entorno |
|---|---|
| PR hacia `develop` | dev |
| push a `develop` (merge o directo) | stage |
| push a `main` | prod |
| PR hacia `main` | solo CI, sin deploy |

## Consecuencias
- Cada etapa se ve y falla por separado. Con `--wait`, el deploy falla si un contenedor no queda sano.
- Se usa ssh nativo: ninguna acción de terceros tiene acceso a la llave privada.
- `concurrency` deja un solo deploy por entorno a la vez.
- dev refleja el último PR actualizado, que no necesariamente es `develop`.
- La EC2 clona por HTTPS porque el repo es público. Si pasa a privado, hace falta una deploy key.
