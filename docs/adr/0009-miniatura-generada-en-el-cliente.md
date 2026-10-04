# 0009. La miniatura de 300 px la genera el cliente

- Estado: aceptado
- Fecha: 04/10/2026
- Directriz o regla relacionada: DT-ALM-04, DT-OFF-08, DT-SEC-08, RN-ARC-02

## Contexto
DT-ALM-04 pide guardar cada foto en dos variantes: el original comprimido y una miniatura de 300 px. `ImageIO` de Java no lee WebP sin una librería extra. El cliente, en cambio, ya decodifica la foto en un `canvas` para comprimirla antes de guardarla (DT-OFF-08).

## Decisión
- El componente de captura del frontend (`app-captura-foto`) genera las dos variantes en el dispositivo: lado mayor de 1600 px y de 300 px, en WebP, o en JPEG si el navegador no codifica WebP.
- `ArchivoService.guardarFoto` recibe las dos, valida el tipo de cada una por su firma binaria (DT-SEC-08) y las guarda juntas: `<uuid>.webp` y `<uuid>-miniatura.webp`.

## Consecuencias
- El servidor no decodifica imágenes: no necesita una librería de WebP ni gasta CPU en cada subida.
- La miniatura también viaja en la sincronización offline: unos 15 KB más por foto.
- El servidor no comprueba las dimensiones de la miniatura. Si eso llega a importar, se agrega una librería de WebP al backend y se valida el tamaño.
