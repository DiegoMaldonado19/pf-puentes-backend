package com.cunoc.puentes;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;

/**
 * Recorrido optativo: ./mvnw -Dtest=RecorridoP8Navegador test. El nombre no coincide con los
 * patrones de la suite normal: no obliga al CI backend a instalar Node/Chromium ni omite casos.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "management.server.port=0",
      "sgp.cors.origenes=http://127.0.0.1:8093",
      "sgp.renovacion.cookie-segura=false"
    })
@Sql({"/indice/consulta-pesos.sql", "/mantenimiento/seguimiento-rutinario.sql"})
class RecorridoP8Navegador extends PruebaIntegracion {
  @LocalServerPort int puerto;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder codificador;

  @Test
  void recorreFrontendYApiRealesConDatosDesechables() throws Exception {
    Path frontend =
        Path.of(System.getenv().getOrDefault("P8_FRONTEND_DIR", "../pf-puentes-frontend"))
            .toAbsolutePath()
            .normalize();
    assertThat(frontend.resolve("playwright.p8.config.ts")).exists();
    String clave = "Prueba-P8-navegador-2026!";
    jdbc.update(
        "UPDATE usuario SET contrasena_hash = ? WHERE correo LIKE '%@anual-prueba.com'",
        codificador.encode(clave));
    jdbc.update(
        """
        INSERT INTO orden_mantenimiento (id,puente_id,tipo,estado,descripcion,prioridad)
        VALUES (?,?,'CORRECTIVO','PROPUESTA','Propuesta de descarte del recorrido','MEDIA')
        """,
        id(22),
        id(1));
    for (int numero : new int[] {23, 24}) {
      jdbc.update(
          """
          INSERT INTO orden_mantenimiento (id,puente_id,tipo,estado,descripcion,prioridad,fecha_programada)
          VALUES (?,?,'RUTINARIO','PROGRAMADA',?,'MEDIA',CURRENT_DATE)
          """,
          id(numero),
          id(1),
          "Rutina para cancelar " + numero);
    }
    Long ordenesAntes = jdbc.queryForObject("SELECT count(*) FROM orden_mantenimiento", Long.class);
    UUID sieca = UUID.fromString("0192b000-0000-7000-8000-000000000001");
    var esquemaAntes = jdbc.queryForMap("SELECT * FROM formulario_version WHERE id=?", sieca);
    var juegosAntes = jdbc.queryForList("SELECT * FROM version_pesos");
    var pesosAntes = jdbc.queryForList("SELECT * FROM peso_elemento");
    var inspeccionesAntes = jdbc.queryForList("SELECT * FROM inspeccion");
    Files.createDirectories(frontend.resolve("test-results"));
    var constructor =
        new ProcessBuilder(
                "npm", "exec", "--", "playwright", "test", "-c", "playwright.p8.config.ts")
            .directory(frontend.toFile())
            .redirectErrorStream(true)
            .redirectOutput(frontend.resolve("test-results/p8-ejecucion.log").toFile());
    constructor.environment().put("P8_BACKEND_URL", "http://127.0.0.1:" + puerto);
    constructor.environment().put("P8_RECORRIDO_AISLADO", "1");
    constructor.environment().put("P8_CLAVE_PRUEBA", clave);
    String hoy = LocalDate.now(ZoneOffset.UTC).toString();
    constructor.environment().put("P8_HOY", hoy);
    Process proceso = constructor.start();
    try {
      assertThat(proceso.waitFor(240, TimeUnit.SECONDS))
          .as("Playwright debe terminar antes de cuatro minutos")
          .isTrue();
      assertThat(proceso.exitValue()).as("Recorrido real del navegador").isZero();
    } finally {
      proceso.descendants().forEach(ProcessHandle::destroy);
      proceso.destroy();
    }
    assertThat(
            jdbc.queryForMap(
                "SELECT estado,prioridad,responsable,fecha_ejecucion::text AS fecha FROM orden_mantenimiento WHERE id = ?",
                id(18)))
        .containsEntry("estado", "EJECUTADA")
        .containsEntry("prioridad", "ALTA")
        .containsEntry("responsable", "Responsable del recorrido P8")
        .containsEntry("fecha", hoy);
    assertThat(
            jdbc.queryForMap("SELECT estado,motivo FROM orden_mantenimiento WHERE id = ?", id(22)))
        .containsEntry("estado", "DESCARTADA")
        .containsEntry("motivo", "Intervención descartada durante el recorrido aislado");
    for (int numero : new int[] {23, 24}) {
      assertThat(
              jdbc.queryForMap(
                  "SELECT estado,motivo,fecha_programada::text AS fecha FROM orden_mantenimiento WHERE id=?",
                  id(numero)))
          .containsEntry("estado", "CANCELADA")
          .containsEntry("motivo", "Cancelación del recorrido " + numero)
          .containsEntry("fecha", hoy);
      assertThat(
              jdbc.queryForMap(
                  "SELECT valor_anterior->>'estado' AS anterior,valor_nuevo->>'estado' AS nuevo,valor_nuevo->>'motivo' AS motivo FROM auditoria WHERE entidad='orden_mantenimiento' AND entidad_id=?",
                  id(numero)))
          .containsEntry("anterior", "PROGRAMADA")
          .containsEntry("nuevo", "CANCELADA")
          .containsEntry("motivo", "Cancelación del recorrido " + numero);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM archivo WHERE orden_mantenimiento_id = ?",
                Long.class,
                id(18)))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad = 'orden_mantenimiento' AND entidad_id = ?",
                Long.class,
                id(18)))
        .isGreaterThanOrEqualTo(5L);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM orden_mantenimiento", Long.class))
        .isEqualTo(ordenesAntes);
    assertThat(jdbc.queryForMap("SELECT * FROM formulario_version WHERE id=?", sieca))
        .isEqualTo(esquemaAntes);
    assertThat(jdbc.queryForList("SELECT * FROM inspeccion"))
        .containsExactlyInAnyOrderElementsOf(inspeccionesAntes);
    assertThat(
            jdbc.queryForList("SELECT * FROM version_pesos WHERE formulario_version_id<>?", sieca))
        .containsExactlyInAnyOrderElementsOf(juegosAntes);
    assertThat(
            jdbc.queryForList(
                "SELECT p.* FROM peso_elemento p JOIN version_pesos v ON v.id=p.version_pesos_id WHERE v.formulario_version_id<>?",
                sieca))
        .containsExactlyInAnyOrderElementsOf(pesosAntes);
    var creados =
        jdbc.queryForList(
            "SELECT id,acta FROM version_pesos WHERE formulario_version_id=? ORDER BY id", sieca);
    assertThat(creados).hasSize(2);
    assertThat(creados.getFirst().get("acta")).isEqualTo("Referencia de prueba del navegador");
    assertThat(creados.getLast().get("acta")).isNull();
    for (int i = 0; i < creados.size(); i++) {
      UUID juego = (UUID) creados.get(i).get("id");
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM peso_elemento WHERE version_pesos_id=?", Long.class, juego))
          .isEqualTo(19L);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM peso_elemento WHERE version_pesos_id=? AND peso=?",
                  Long.class,
                  juego,
                  i == 0 ? new java.math.BigDecimal("2.125") : new java.math.BigDecimal("3.5")))
          .isEqualTo(19L);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM auditoria WHERE entidad='version_pesos' AND entidad_id=?",
                  Long.class,
                  juego))
          .isEqualTo(1L);
    }
    assertThat(Files.exists(frontend.resolve("playwright-report/p8/index.html"))).isTrue();
  }

  private static UUID id(int numero) {
    return UUID.fromString("019a0035-0000-7000-8000-%012d".formatted(numero));
  }
}
