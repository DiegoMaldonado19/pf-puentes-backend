# 0061. Coordenadas de puentes en Malo públicas, sin restricción por estado

- Estado: propuesto
- Fecha: 28/10/2026
- Directriz o regla relacionada: DT-SEC-13 (opcional) y RN-USR-10

## Contexto
DT-SEC-13 dice que las coordenadas exactas de los puentes en Malo *pueden* restringirse a usuarios autenticados, por ser infraestructura crítica (pendiente #16). Hoy `GET /api/v1/puentes` y `GET /api/v1/puentes/{id}` son rutas públicas y devuelven `latitud`, `longitud` y `utm` de todos los puentes activos, sin mirar `estado_actual`. RN-USR-10 declara público el inventario con su ubicación y su estado semafórico.

## Decisión
No se restringen las coordenadas de los puentes en Malo. RN-USR-10 ya hace públicos el estado y la ubicación, y el mapa público (ACT-01) los necesita. Lo sensible, es decir daños, fotos e informes, sigue detrás de la sesión. El código ya se comporta así; este ADR lo registra.

## Consecuencias
- Se gana un solo criterio de visibilidad: ninguna capa oculta coordenadas según el estado, ni `PuenteService`, ni `PuenteMapper`, ni el mapa público.
- Se pierde la reserva del punto exacto de los puentes en Malo. Restringir solo las coordenadas dejaría casi todo visible: `GET /api/v1/puentes?estado=MALO` ya lista esos puentes con nombre, código, ruta y municipio.
- Es una desviación de la opción de DT-SEC-13, no de una obligación ("pueden").
- Para revertirlo, filtrar `latitud`, `longitud` y `utm` por rol cuando `estado_actual` sea `MALO`, y ocultar esos marcadores en el mapa público. Requiere un ADR nuevo que reemplace este.
