package com.cunoc.puentes;

import static org.assertj.core.api.Assertions.assertThat;

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

  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void limpiar() {
    jdbc.update("DELETE FROM asignacion WHERE id::text LIKE ?", DEMO);
    jdbc.update("DELETE FROM inscripcion WHERE id::text LIKE ?", DEMO);
    jdbc.update("DELETE FROM curso WHERE id::text LIKE ?", DEMO);
    jdbc.update("DELETE FROM puente WHERE id::text LIKE ?", DEMO);
    jdbc.update("DELETE FROM usuario WHERE id::text LIKE ?", DEMO);
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
  }
}
