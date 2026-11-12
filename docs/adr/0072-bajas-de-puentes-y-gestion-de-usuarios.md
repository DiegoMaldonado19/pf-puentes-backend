# 0072. Bajas de puentes con fecha y autor, y gestión administrativa de usuarios

- Estado: aceptado
- Fecha: 11/11/2026
- Revisa: ADR 0010 (modelo de datos base), ADR 0061 (coordenadas de puentes en MALO públicas)

## Contexto
Al dar de baja un puente, desaparecía de todas las pantallas sin forma de verlo ni de
reactivarlo. El backend ya aceptaba `incluir_bajas`, pero ninguna pantalla lo enviaba. Los
usuarios tampoco se podían crear, editar, activar ni desbloquear desde la interfaz.

## Decisión
### Puentes
- `GET /api/v1/puentes?situacion=ACTIVOS|BAJAS|TODOS`. `incluir_bajas` queda como alias.
- La migración `V2026_11_11_1000__registrar_fecha_y_autor_de_baja_de_puente.sql` agrega
  `baja_en` y `baja_por`, con `COMMENT ON`.
- `POST /api/v1/puentes/{id}/reactivacion` exige motivo y queda auditado con la acción
  `REACTIVAR`.

### Usuarios
- `POST /api/v1/usuarios` crea la cuenta y envía un enlace para fijar la contraseña, reutilizando
  el flujo de recuperación.
- `PUT /api/v1/usuarios/{id}` edita nombre y correo. El rol solo cambia entre CATEDRATICO y
  ADMINISTRADOR. Nadie cambia su propio rol ni deja el sistema sin administrador.
- `POST /api/v1/usuarios/{id}/desbloqueo` y `/reenvio-verificacion`.
- «Eliminar» sigue siendo la baja lógica existente.

## Consecuencias
- Un puente dado de baja aparece en el filtro «Dados de baja», con fecha y autor, y puede
  reactivarse con un motivo.
- Las acciones administrativas sobre usuarios están cubiertas por pruebas WebMvc por rol y por
  `AutorizacionPorEndpointTest`.
