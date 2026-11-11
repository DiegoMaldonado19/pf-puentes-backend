package com.cunoc.puentes;

import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.indice.MetadatosDanoFormulario;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.utility.MountableFile;

/** scripts/datos-demo.sql corre con psql sobre el esquema real, dos veces sin duplicar. */
@SpringBootTest
class DatosDemoTest extends PruebaIntegracion {

  private static final UUID ADMIN = UUID.fromString("0199ff00-0000-7000-8000-0000000000ff");
  private static final String DEMO = "019a2a00-%";
  private static final UUID VERSION_SEMILLA =
      UUID.fromString("0192b000-0000-7000-8000-000000000001");
  private static final UUID JUEGO_DEMO = UUID.fromString("019a2a00-0000-7000-8000-000000000050");

  @Autowired JdbcTemplate jdbc;
  @Autowired MetadatosDanoFormulario metadatos;

  @AfterEach
  void limpiar() {
    jdbc.update("DELETE FROM asignacion WHERE id::text LIKE ?", DEMO);
    jdbc.update("DELETE FROM inscripcion WHERE id::text LIKE ?", DEMO);
    jdbc.update("DELETE FROM curso WHERE id::text LIKE ?", DEMO);
    jdbc.update(
        "DELETE FROM orden_mantenimiento WHERE puente_id IN (SELECT id FROM puente WHERE id::text LIKE ?)",
        DEMO);
    jdbc.update(
        "DELETE FROM peso_elemento WHERE version_pesos_id IN (SELECT id FROM version_pesos"
            + " WHERE id::text LIKE ?)",
        DEMO);
    jdbc.update("DELETE FROM version_pesos WHERE id::text LIKE ?", DEMO);
    jdbc.update("DELETE FROM puente WHERE id::text LIKE ?", DEMO);
    jdbc.update(
        "DELETE FROM notificacion WHERE usuario_id IN (SELECT id FROM usuario WHERE id::text LIKE ?)",
        DEMO);
    jdbc.update("DELETE FROM usuario WHERE id::text LIKE ?", DEMO);
    jdbc.update(
        "DELETE FROM notificacion WHERE usuario_id IN (SELECT id FROM usuario WHERE id = ?)",
        ADMIN);
    jdbc.update("DELETE FROM usuario WHERE id = ?", ADMIN);
  }

  @Test
  void cargaUnaCuentaPorRolConLaClaveDelAdministradorYElPuenteAsignado() throws Exception {
    jdbc.update(
        "INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol, estado,"
            + " correo_verificado_en, creado_en) VALUES (?, 'admin.demo@sgp.gt', 'Admin',"
            + " 'hash-admin', 'ADMINISTRADOR', 'ACTIVO', now(), '2000-01-01')",
        ADMIN);
    POSTGRES.copyFileToContainer(
        MountableFile.forHostPath("scripts/datos-demo.sql"), "/tmp/datos-demo.sql");

    for (int corrida = 0; corrida < 2; corrida++) {
      var resultado =
          POSTGRES.execInContainer(
              "psql",
              "-U",
              POSTGRES.getUsername(),
              "-d",
              POSTGRES.getDatabaseName(),
              "-v",
              "ON_ERROR_STOP=1",
              "-q",
              "-f",
              "/tmp/datos-demo.sql");
      assertThat(resultado.getExitCode()).as(resultado.getStderr()).isZero();
    }

    assertThat(
            jdbc.queryForList(
                "SELECT rol FROM usuario WHERE id::text LIKE ? AND contrasena_hash = 'hash-admin'",
                String.class,
                DEMO))
        .containsExactlyInAnyOrder("CATEDRATICO", "ESTUDIANTE", "PROFESIONAL_EXTERNO");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM asignacion WHERE id::text LIKE ?", Long.class, DEMO))
        .isOne();

    // Los pesos de V1.0.0 cubren exactamente los campos de daño que calcula el servidor (B9)
    assertThat(
            jdbc.queryForList(
                "SELECT elemento_ref FROM peso_elemento WHERE version_pesos_id = ?",
                String.class,
                JUEGO_DEMO))
        .containsExactlyInAnyOrderElementsOf(metadatos.obtener(VERSION_SEMILLA).referencias());
    // Dos puentes a menos de 100 m del de demostración (B8) y uno dado de baja con fecha y autor
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM puente a JOIN puente b ON a.id <> b.id"
                    + " AND a.id::text LIKE ? AND b.id::text LIKE ?"
                    + " AND ST_DWithin(a.ubicacion, b.ubicacion, 100)",
                Long.class,
                DEMO,
                DEMO))
        .isPositive();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM puente WHERE id::text LIKE ? AND NOT activo"
                    + " AND baja_en IS NOT NULL AND baja_por IS NOT NULL",
                Long.class,
                DEMO))
        .isOne();
    // Órdenes en cuatro estados distintos (B9)
    assertThat(
            jdbc.queryForList(
                "SELECT estado FROM orden_mantenimiento WHERE id::text LIKE ?", String.class, DEMO))
        .containsExactlyInAnyOrder("PROPUESTA", "PROGRAMADA", "EJECUTADA", "DESCARTADA");
  }
}
