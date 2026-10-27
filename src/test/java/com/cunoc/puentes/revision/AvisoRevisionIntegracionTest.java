package com.cunoc.puentes.revision;

import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.PruebaIntegracion;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Sql({"/curso/datos.sql", "/revision/datos.sql"})
@Sql(
    scripts = {"/revision/limpiar.sql", "/curso/limpiar.sql"},
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AvisoRevisionIntegracionTest extends PruebaIntegracion {
  @Autowired AvisoRevisionService servicio;
  @Autowired JdbcClient jdbc;
  private static final Instant AHORA = Instant.parse("2026-10-27T12:30:00Z");
  private static final UUID ID = UUID.fromString("0199b700-0000-7000-8000-000000000011");

  @Test
  void limiteExactoDeQuinceDiasYNoDuplicaElMismoEnvio() {
    jdbc.sql("update inspeccion set enviada_en = :fecha where id = :id")
        .param("fecha", java.sql.Timestamp.from(AHORA.minusSeconds(15 * 86400L).plusSeconds(1)))
        .param("id", ID)
        .update();
    assertThat(servicio.avisar(AHORA)).isZero();
    assertThat(servicio.avisar(AHORA.plusSeconds(1))).isEqualTo(1);
    assertThat(servicio.avisar(AHORA.plusSeconds(3600))).isZero();
    assertThat(
            jdbc.sql("select count(*) from notificacion where tipo = 'REVISION_VENCIDA'")
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void nuevoEnvioPermiteNuevoAvisoPeroRechazadasNoGeneranAvisos() {
    jdbc.sql("update inspeccion set enviada_en = :fecha where id = :id")
        .param("fecha", java.sql.Timestamp.from(AHORA.minusSeconds(30 * 86400L)))
        .param("id", ID)
        .update();
    assertThat(servicio.avisar(AHORA)).isEqualTo(1);
    jdbc.sql("update inspeccion set enviada_en = :fecha where id = :id")
        .param("fecha", java.sql.Timestamp.from(AHORA.minusSeconds(16 * 86400L)))
        .param("id", ID)
        .update();
    assertThat(servicio.avisar(AHORA)).isEqualTo(1);
    jdbc.sql("update inspeccion set estado = 'RECHAZADA', enviada_en = :fecha where id = :id")
        .param("fecha", java.sql.Timestamp.from(AHORA.minusSeconds(17 * 86400L)))
        .param("id", ID)
        .update();
    assertThat(servicio.avisar(AHORA)).isZero();
  }
}
