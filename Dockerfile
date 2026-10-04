# dev: lo usa compose.local.yaml con hot reload
FROM maven:3.9.16-eclipse-temurin-25 AS dev
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
CMD ["mvn", "-B", "spring-boot:run"]

FROM dev AS build
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:25.0.4_7-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --start-period=60s CMD wget -qO- http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
