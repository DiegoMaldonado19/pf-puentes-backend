# 0071. El envío de una inspección queda bloqueado mientras haya observaciones sin resolver

- Estado: aceptado
- Fecha: 11/11/2026
- Revisa: ADR 0057 (seguimiento de calificación privada y avisos de revisión), ADR 0056
  (veredictos y observaciones ancladas)

## Contexto
Cuando el revisor pedía cambios con observaciones ancladas a campos, el autor podía reenviar
la inspección sin corregirlas. El revisor volvía a encontrar lo mismo. Además, el motivo del
veredicto superior a 500 caracteres provocaba un error 500 al copiarlo.

## Decisión
- `EnvioInspeccionService.exigirPublicable` responde 422 con
  `errores[{campo: "observaciones-pendientes"}]` si quedan observaciones ancladas sin resolver.
  La comprobación entra por el puerto `inspeccion.puerto.ConsultaRevision`, no por
  `MaquinaEstadosInspeccion`, que exige cobertura del 100 %.
- `InspeccionDTO.revision` (aditivo) expone `ultimo_veredicto`, `observaciones_pendientes` y
  `emitido_en`, para que el autor vea el bloqueo antes de enviar.
- El motivo de la decisión se trunca a 500 caracteres al copiarlo a la transición y a la
  auditoría. La auditoría es append-only y no se migra.
- El autor recibe una notificación con enlace a `/inspecciones/{id}` cuando se le piden
  cambios (`NotificacionService.notificar`).

## Consecuencias
- El envío no se puede hacer hasta que cada observación esté resuelta en el borrador; la
  resolución es del autor (`PATCH /api/v1/observaciones/{id}/resolucion`).
- Una inspección ya enviada no se puede resolver: la respuesta es 409, como antes.
