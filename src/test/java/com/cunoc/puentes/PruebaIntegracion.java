package com.cunoc.puentes;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de las pruebas de integración: PostgreSQL + PostGIS y MinIO reales (DT-CAL-02).
 *
 * <p>Los contenedores son estáticos y no son beans: toda la suite comparte uno de cada uno, aunque
 * haya varios contextos de Spring. Los borra Ryuk al terminar la JVM.
 */
public abstract class PruebaIntegracion {

  @ServiceConnection
  protected static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:17-3.6-alpine")
              .asCompatibleSubstituteFor("postgres"));

  protected static final MinIOContainer MINIO =
      new MinIOContainer(
          DockerImageName.parse("pgsty/minio:RELEASE.2026-08-04T00-00-00Z")
              .asCompatibleSubstituteFor("minio/minio"));

  static {
    Startables.deepStart(POSTGRES, MINIO).join();
  }

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry registry) {
    registry.add("spring.flyway.user", POSTGRES::getUsername);
    registry.add("spring.flyway.password", POSTGRES::getPassword);
    registry.add("sgp.minio.url", MINIO::getS3URL);
    registry.add("sgp.minio.usuario", MINIO::getUserName);
    registry.add("sgp.minio.clave", MINIO::getPassword);
  }
}
