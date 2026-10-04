# 0007. Usuarios de base de datos

- Estado: aceptado
- Fecha: 03/10/2026
- Directriz o regla relacionada: DT-BD-13, DT-BD-01

## Contexto
La aplicación debe conectarse sin privilegios DDL, y las migraciones deben correr con otro usuario. Instalar `postgis` exige un superusuario.

## Decisión
- `puentes_migrador` es el `POSTGRES_USER`: es el dueño del esquema y lo usa Flyway.
- `docker/postgres/01-usuario-app.sh` crea `puentes_app` al inicializar el volumen, con `USAGE` sobre `public` y privilegios por defecto de solo DML sobre lo que cree el migrador.
- La aplicación se conecta como `puentes_app`.

## Consecuencias
- Aunque la aplicación quede comprometida, no puede alterar el esquema.
- Las extensiones se crean en una migración versionada.
- El script solo corre con el volumen vacío. Para cambiar la contraseña después: `ALTER ROLE puentes_app PASSWORD '...'`.
- Testcontainers usa un solo usuario (superusuario), así que las pruebas no validan los permisos de `puentes_app`.
