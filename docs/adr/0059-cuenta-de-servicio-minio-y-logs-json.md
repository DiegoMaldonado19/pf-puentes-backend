# 0059. Cuenta de servicio de MinIO, contenedor como uid 1000 y bitácoras JSON rotadas

- Estado: aceptado
- Fecha: 28/10/2026
- Directriz o regla relacionada: DT-ARQ-03, DT-BE-15, DT-DEP-09, DT-DEP-11, DT-DEP-08 y RN-INS-06

## Contexto
DT-DEP-11 pide servicios sin privilegios; el backend se conectaba a MinIO con la cuenta root. DT-DEP-09 pide bitácoras JSON rotadas de 90 días; los logs de contenedor se pierden en cada redeploy.

## Decisión
El backend usa una cuenta de servicio con permisos solo sobre el bucket `archivos` (script `scripts/minio-cuenta-servicio.sh`). MinIO corre como uid 1000 con el entrypoint de la imagen (`MINIO_USERNAME`/`MINIO_UID`); el entrypoint de `compose.yaml` cambia el dueño de `/data` solo si hace falta. Las bitácoras van en formato ECS al volumen `logs` con rotación de 90 días. Si la cuenta no está configurada, el backend cae a la cuenta root, para que el despliegue no se rompa.

## Consecuencias
Los dos secretos de la cuenta se cargan juntos en el Environment. El pipeline espera PostgreSQL y MinIO, crea o actualiza la cuenta mediante el script ejecutable y solo después inicia el backend. El bootstrap falla ante credenciales incompletas o un usuario igual al root, y no elimina la cuenta antes de actualizarla. El primer arranque de un volumen heredado tarda más por el `chown` recursivo. La cuenta de servicio no puede crear buckets ni administrar usuarios; la verificación está en la guía de pruebas.
