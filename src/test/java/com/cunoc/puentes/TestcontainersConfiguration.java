package com.cunoc.puentes;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** PostgreSQL + PostGIS real para las pruebas (DT-CAL-02). Se importa con {@code @Import}. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return new PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:17-3.6-alpine")
            .asCompatibleSubstituteFor("postgres"));
  }

  @Bean
  DynamicPropertyRegistrar credencialesFlyway(PostgreSQLContainer postgres) {
    return registry -> {
      registry.add("spring.flyway.user", postgres::getUsername);
      registry.add("spring.flyway.password", postgres::getPassword);
    };
  }
}
