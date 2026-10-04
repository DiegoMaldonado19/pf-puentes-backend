# 0001. Despliegue en una EC2 con un proyecto Compose por entorno

- Estado: aceptado
- Fecha: 03/10/2026
- Directriz o regla relacionada: DT-DEP-01, DT-DEP-02, DT-DEP-06, pendiente #15

## Contexto
DT-DEP-01 pide un servidor de la universidad con Docker Compose, pero la sección 9.4 exige entregar en una nube pública. Necesitamos tres entornos (dev, stage y prod) con bases de datos separadas, a costo de estudiante.

## Decisión
Una sola instancia EC2 con Docker Compose. Cada entorno tiene:
- su clon del repo en `~/puentes/<entorno>/pf-puentes-backend`, con su propio `.env`;
- su proyecto Compose `puentes-<entorno>-backend`, con sus propios volúmenes;
- la red externa `puentes-<entorno>`, compartida con el frontend del mismo entorno.

El mismo `compose.yaml` sirve a los tres entornos; solo cambia el `.env`. Ni el backend, ni postgres, ni minio publican puertos: solo nginx (repo frontend).

## Consecuencias
- Es el mismo Compose que se usaría on-premise. Migrar al servidor universitario es clonar el repo y escribir el `.env`.
- Ningún entorno toca la base de datos de otro (DT-DEP-06).
- Los tres entornos comparten la CPU y la memoria de la instancia: el mínimo es t3.medium (4 GB). Si la instancia cae, caen los tres.
- Prerrequisitos de la EC2:
  - Docker con el compose plugin, y git;
  - el usuario de SSH en el grupo `docker`;
  - una Elastic IP, porque `EC2_HOST` no debe cambiar;
  - el security group abierto en 22, 80, 8081 y 8082.
- Para separar entornos en instancias distintas basta con definir `EC2_HOST` dentro de cada Environment de GitHub, porque un secreto del Environment sobreescribe al del repositorio.
