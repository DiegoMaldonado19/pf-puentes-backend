# 0062. Identificación del puente desde el inventario, no desde la captura

- Estado: aceptado
- Fecha: 28/10/2026
- Directriz o regla relacionada: RN-INS-09 y RN-INV-03

## Contexto
Los cinco campos de `identificacion` del esquema SIECA (`codigo`, `nombre`, `ruta`, `departamento` y `municipio`) son `readOnly`. Su valor debe venir del puente y del catálogo INE, no de lo que escriba el inspector. La precarga de RN-INS-09 solo copiaba los permanentes de la última inspección publicada.

## Decisión
`ReferenciasInspeccion.identificacion` lee esos valores del puente y del catálogo INE. Los fusiona en la precarga (`GET /api/v1/inspecciones/{id}/datos-permanentes` y `GET /api/v1/puentes/{puenteId}/datos-permanentes`) y los escribe al confirmar (`POST /api/v1/inspecciones/{id}/confirmacion-datos-permanentes`), donde reemplazan lo que envíe el cliente.

## Consecuencias
- Se gana: al confirmar, la sección 1 coincide con el inventario y el catálogo INE. Un cambio del puente se refleja en la siguiente precarga o confirmación. Las inspecciones publicadas no cambian.
- El borrador (`PUT /api/v1/inspecciones/{id}`) acepta lo que mande el cliente, pero el envío (`EnvioInspeccionService`) valida y guarda la sección 1 del inventario: lo enviado y lo publicado siempre coinciden con el puente (`InspeccionSiecaTest.elEnvioConservaLaIdentificacionDelInventario`). Un envío rechazado no modifica la fila.
- Si se revierte: quitar la fusión en `confirmar` y en `armar`. Los cinco campos dependerían de lo que envíe el cliente.
