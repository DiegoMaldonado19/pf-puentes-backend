# 0068. Esquema base en código y campos adicionales compuestos sobre él

- Estado: aceptado
- Fecha: 11/11/2026
- Revisa: ADR 0040 (creación de pesos), ADR 0058 (validador de formulario desde el esquema)

## Contexto
Para cambiar el formulario, el administrador tenía que escribir el JSON Schema completo
en un editor de texto. Un error de sintaxis o de estructura solo aparecía al guardar, y
una versión nueva sin pesos no calculaba el índice de condición: sus inspecciones no se
publicaban y no generaban órdenes.

## Decisión
1. **Esquema base en código.** `formulario/formulario-sieca-v1.json` es la fuente del
   formulario SIECA base. Un test compara ese recurso con la fila `ACTIVA` sembrada por la
   migración `V2026_10_23_1000`; la fila no se modifica.
2. **Composición.** `ComposicionEsquemaService` añade a una copia del base los campos
   `campos_extra` (`seccion`, `clave`, `titulo`, `tipo`, `obligatorio`, `opciones`,
   `minimo`, `maximo`, `unidad`, `ayuda`). Cada campo se marca `x-extra`. Los tipos son
   texto, texto largo, número, entero, sí/no, opción y fecha.
3. **Restricciones.** Las secciones `identificacion` y `tramos` están cerradas. La clave
   cumple `^[a-z][a-z0-9_]{2,40}$`, es única por sección y no repite un campo del base. Hay
   un máximo de 40 campos adicionales. Los campos se construyen solo desde atributos tipados,
   así que nunca aparecen `x-dano`, `x-permanente`, `x-grupo`, `x-anulacion-critica` ni
   `x-elemento-ref`: el índice, los pesos y la precarga no cambian.
4. **API.** `GET /api/v1/formularios/base` devuelve el esquema y las secciones que admiten
   campos. `POST /api/v1/formularios/validacion` valida en seco y no guarda nada.
   `POST` y `PUT /api/v1/formularios` aceptan `campos_extra`; si llegan, tienen prioridad
   sobre `esquema`, que sigue aceptándose para el uso avanzado.
5. **Clonado del juego de pesos al publicar.** `FormularioVersionService.publicar` publica
   `FormularioPublicado(anteriorId, nuevoId)` dentro de la transacción. `indice` copia el
   juego vigente de la versión anterior a la nueva con el mismo acta. Si la nueva ya tiene
   un juego propio, se respeta. Si el juego copiado no cubre exactamente sus campos de daño,
   la publicación se rechaza con 422 `juego-pesos-incompleto`.
6. **Mensajes.** Los errores de validación de inspecciones nombran el campo con su título
   (`Longitud Total del Puente (m) es obligatorio.`), en lugar de `Requerido`.

## Consecuencias
- Una versión creada desde el constructor siempre calcula IC, porque hereda el juego vigente.
- Quien necesite un esquema distinto al base sigue pudiendo enviarlo completo, bajo su
  propia responsabilidad.
- Migrar borradores a la versión vigente y `additionalProperties: false` quedan fuera de
  alcance: chocan con RN-FRM-05, la idempotencia y las colas offline.
