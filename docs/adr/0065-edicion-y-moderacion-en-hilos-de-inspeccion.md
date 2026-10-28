# 0065. Edición, borrado y moderación también en hilos de inspección; menciones por id

- Estado: aceptado
- Fecha: 28/10/2026
- Directriz o regla relacionada: RN-FOR-05, RN-FOR-08, RN-FOR-10

## Contexto
`ForoService` respondía 404 al editar, borrar u ocultar un comentario de un hilo de inspección, y al cerrar ese hilo. RN-FOR-05 y RN-FOR-08 no distinguen entre hilos de puente y de inspección, y el frontend ya ofrecía esas acciones en la discusión de la inspección: el 404 se mostraba como "inspección no publicada".

Para RN-FOR-10 el backend ya notificaba las menciones `@{uuid}` del texto, pero no había forma de elegir a la persona: nadie escribe un UUID a mano.

## Decisión
- Editar (15 minutos), borrar y ocultar valen igual en los dos tipos de hilo, con las mismas reglas de autor y de rol. Cerrar un hilo de inspección bloquea los comentarios nuevos con el 409 que ya existía.
- `GET /api/v1/usuarios/mencionables` devuelve id y nombre de las cuentas activas, para cualquier rol con sesión. El frontend arma el texto con `@{uuid}` a partir de la persona elegida.

## Consecuencias
- Se gana: el foro de inspección tiene la misma moderación auditada que el del puente.
- Cualquier usuario autenticado ve los nombres de las cuentas activas, nunca su correo ni su rol.
- La lista es completa, sin paginación. Si las cuentas pasan de unos cientos, conviene buscar en el servidor.
