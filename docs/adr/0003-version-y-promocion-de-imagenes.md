# 0003. Versión y promoción de imágenes por contenido

- Estado: aceptado
- Fecha: 03/10/2026
- Directriz o regla relacionada: DT-DEP-06

## Contexto
Queremos construir cada imagen una sola vez, identificarla por el número de build y promoverla de dev a stage y a prod sin reconstruirla. Como varios PRs se despliegan en dev, lo que corre en dev no siempre es lo que se fusiona.

## Decisión
La identidad de una imagen es el **árbol git** del código (`git rev-parse HEAD^{tree}`), que no cambia al hacer merge commit ni squash. Cada imagen se publica con:
- `:<run_number>`: la versión, es decir, el número de build;
- `:tree-<hash>`: el índice por contenido;
- el label `org.opencontainers.image.version=<run_number>`.

En el job `push`:
1. Si `:tree-<hash>` ya existe, se promueve esa imagen y su versión se lee del label.
2. Si no existe, se construye con el número de este build.
3. **Excepción, prod:** si no existe, el job falla. A prod solo llega lo que pasó por stage.

## Consecuencias
- prod corre exactamente los mismos bytes que se probaron en stage, y stage reutiliza la imagen del PR si `develop` quedó idéntico. La regla "Require branches to be up to date" en `develop` lo garantiza.
- Si `develop` cambió entre medio, stage construye una imagen nueva, que es lo correcto porque ese contenido nunca se probó.
- Un hotfix directo a `main` falla: tiene que pasar por `develop`.
- Cada imagen lleva dos tags en Docker Hub. Se construye con `provenance: false` para que el manifiesto sea simple y el label se pueda leer.
