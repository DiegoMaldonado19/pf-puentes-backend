# 0017. Token de renovación sin rotación

- Estado: aceptado
- Fecha: 17/10/2026
- Directriz o regla relacionada: DT-SEC-01, DT-SEC-02, RN-USR-08

## Contexto
Al renovar la sesión se puede emitir un token de renovación nuevo cada vez (rotación) y detectar el reuso de uno viejo como robo. Eso exige guardar la cadena de reemplazo (qué token sustituyó a cuál), y la tabla `token_renovacion` no la tiene: solo guarda el hash, el vencimiento y la revocación.

## Decisión
- **No se rota.** `POST /api/v1/auth/renovar` devuelve un token de acceso nuevo y deja la cookie como está; el mismo token de renovación sirve hasta que vence (7 días) o se revoca.
- **Cada inicio de sesión crea su propio token**, así que hay una sesión por dispositivo. Cerrar sesión revoca solo el de esa cookie; `SesionService.revocarTodas(usuarioId)` revoca todos los de la cuenta (la usarán la baja y el cambio de contraseña).
- **La cuenta se comprueba en cada renovación:** si pasó a `INACTIVO`, la renovación se rechaza aunque el token siga vigente.
- **Un solo 401 (`sesion-invalida`)** para cookie ausente, vencida, revocada, inexistente o de una cuenta dada de baja, y la respuesta ordena borrar la cookie. Quien llama no distingue el motivo.
- Renovar y cerrar sesión **no se auditan**: no cambian datos de negocio y llenarían la bitácora.

## Consecuencias
- Un token de renovación robado sirve hasta 7 días, salvo que su dueño cierre sesión o se revoquen todas sus sesiones. Lo mitigan la cookie `HttpOnly`, `Secure` y `SameSite=Strict` con `Path=/api/v1/auth` (DT-SEC-02) y el hecho de que solo se guarda su hash.
- No se detecta el reuso de un token viejo. Si más adelante se quiere rotación, hay que agregar a `token_renovacion` una columna con el token que lo reemplazó y una migración nueva.
- Listar o cerrar sesiones por dispositivo queda fuera de alcance.
