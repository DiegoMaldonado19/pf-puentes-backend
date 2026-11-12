# 0067. El enlace de verificación activa al estudiante

- Estado: aceptado
- Fecha: 11/11/2026
- Revisa: RN-USR-02 (activación del estudiante), ADR 0017 (token de renovación)

## Contexto
Tras registrarse, el estudiante verificaba su correo, pero la cuenta seguía en `PENDIENTE`
y el inicio de sesión respondía 403 `cuenta-pendiente`. Solo un administrador podía
activarla. Además, al reactivar una cuenta dada de baja, la cuenta quedaba `ACTIVA`, así
que el comportamiento era inconsistente.

## Decisión
- `VerificacionCorreoService.verificar` activa al ESTUDIANTE con
  `CambioDeEstadoDeCuenta.activar` además de marcar el correo como verificado.
- Un enlace ya usado de una cuenta verificada responde `{resultado: YA_ACTIVA}` en lugar
  de 422. El segundo uso no cambia nada.
- `POST /api/v1/usuarios/{id}/activacion` permite al administrador activar cuentas
  `PENDIENTE` con correo verificado. Las cuentas sin correo verificado no se activan.
- RN-USR-02 queda así: un estudiante con correo verificado está `ACTIVA`; `PENDIENTE` solo
  significa correo sin verificar o cuenta aún sin activar por el administrador.

## Consecuencias
- Un estudiante puede entrar en cuanto confirma su correo, sin esperar al administrador.
- `ActivadorDeEstudiante` ya no hace nada si la cuenta está `ACTIVA`; la llamada repetida es
  segura.
- Los tests de registro, verificación, activación e inicio de sesión cubren ambos casos.
