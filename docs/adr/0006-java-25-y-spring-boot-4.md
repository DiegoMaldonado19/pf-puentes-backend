# 0006. Java 25 y Spring Boot 4.1

- Estado: aceptado
- Fecha: 03/10/2026
- Directriz o regla relacionada: DT-BE-01, DT-BE-02

## Contexto
DT-BE-01 pide Java 21 LTS y Spring Boot 3.3 o superior, y DT-BE-02 nombra `hypersistence-utils-hibernate-63`. El proyecto se generó con Java 25 y Spring Boot 4.1.1, que trae Hibernate 7.4.

## Decisión
Se mantienen Java 25 (LTS desde septiembre de 2025) y Spring Boot 4.1.1. Para JSONB se usa `hypersistence-utils-hibernate-73` 3.16.0, que está compilada contra Hibernate 7.4: la variante `-63` no funciona con Hibernate 7.

## Consecuencias
- Es la LTS más reciente, con soporte más largo, y Spring Boot 4.1 cumple con "3.3 o superior".
- Nos desviamos de la versión exacta de Java que pide DT-BE-01.
- Spring Boot 4 usa Jackson 3 (`tools.jackson`), así que algunos ejemplos de Boot 3 no aplican tal cual.
- Para revertir: `java.version` a 21, imágenes `temurin` 21 y `java-version` del workflow a 21.
