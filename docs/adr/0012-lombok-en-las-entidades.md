# 0012. Lombok en las entidades JPA

- Estado: aceptado
- Fecha: 04/10/2026
- Directriz o regla relacionada: DT-BE-02, DT-BE-03

## Contexto
Las 25 entidades del modelo base necesitan getters y setters, y escritos a mano suman unas 1400 líneas que nadie lee. El enunciado no menciona Lombok, y DT-BE-02 es la lista de dependencias base, no una lista cerrada.

## Decisión
- Se usa Lombok, con la versión que administra Spring Boot, solo para `@Getter`, `@Setter` y `@NoArgsConstructor(access = PROTECTED)`.
- Nunca `@Data`, `@EqualsAndHashCode` ni `@ToString` en las entidades: con los proxies de Hibernate rompen `equals` y `hashCode` y pueden disparar cargas perezosas.
- En el `pom.xml`, Lombok va antes que MapStruct en el procesador de anotaciones, junto con `lombok-mapstruct-binding`, para que los mappers vean los getters (DT-BE-03).

## Consecuencias
- Las entidades quedan como campos y anotaciones.
- El constructor vacío es protegido, así que solo el código del mismo paquete (el servicio de ese módulo) crea la entidad.
- Para quitar Lombok, `delombok` genera el código Java equivalente.
