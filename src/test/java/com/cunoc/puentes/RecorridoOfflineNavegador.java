package com.cunoc.puentes;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;

/**
 * Recorrido optativo sin conexión: ./mvnw -Dtest=RecorridoOfflineNavegador test. Como
 * RecorridoP8Navegador, su nombre no coincide con los patrones de la suite normal.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "management.server.port=0",
      "sgp.cors.origenes=http://127.0.0.1:8093",
      "sgp.renovacion.cookie-segura=false",
      "sgp.pruebas.formulario-real=true"
    })
@Sql("/inspeccion/sieca.sql")
@Sql(
    scripts = {"/offline/recorrido-limpiar.sql", "/inspeccion/sieca-limpiar.sql"},
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class RecorridoOfflineNavegador extends PruebaIntegracion {
  private static final UUID ESTUDIANTE = UUID.fromString("0199c300-0000-7000-8000-000000000001");
  private static final UUID PUENTE = UUID.fromString("0199c300-0000-7000-8000-000000000010");
  private static final String CORREO = "estudiante-sieca@cunoc.edu.gt";
  private static final String CLAVE = "Prueba-offline-navegador-2026!";
  private static final String CODIGO_PUENTE = "GT-09-0901-9301";

  @LocalServerPort int puerto;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder codificador;

  @Test
  void estudianteCapturaSinConexionYEnviaTrasRedDegradada() throws Exception {
    Path frontend = NavegadorPlaywright.frontend();
    jdbc.update(
        "UPDATE usuario SET contrasena_hash = ? WHERE id = ?",
        codificador.encode(CLAVE),
        ESTUDIANTE);
    NavegadorPlaywright.ejecutar(
        frontend,
        "playwright.offline.config.ts",
        frontend.resolve("test-results/offline-ejecucion.log"),
        Map.of(
            "P8_BACKEND_URL",
            "http://127.0.0.1:" + puerto,
            "P8_RECORRIDO_AISLADO",
            "1",
            "OFFLINE_RECORRIDO_AISLADO",
            "1",
            "OFFLINE_CORREO",
            CORREO,
            "OFFLINE_CLAVE",
            CLAVE,
            "OFFLINE_PUENTE",
            CODIGO_PUENTE));
    var inspecciones =
        jdbc.queryForList(
            """
            SELECT id, estado, datos_permanentes_confirmados_en IS NOT NULL AS permanentes,
                   datos->'identificacion'->>'codigo' AS codigo
            FROM inspeccion WHERE autor_id = ? AND puente_id = ?
            """,
            ESTUDIANTE,
            PUENTE);
    assertThat(inspecciones).hasSize(1);
    assertThat(inspecciones.getFirst())
        .containsEntry("estado", "ENVIADA")
        .containsEntry("permanentes", true)
        .containsEntry("codigo", CODIGO_PUENTE);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM archivo WHERE inspeccion_id = ?",
                Long.class,
                inspecciones.getFirst().get("id")))
        .isEqualTo(4L);
  }
}
