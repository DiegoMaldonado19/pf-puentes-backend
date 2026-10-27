package com.cunoc.puentes.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.notificacion.NotificacionService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Sql({"/curso/datos.sql", "/revision/datos.sql"})
@Sql(
    scripts = {"/revision/limpiar.sql", "/curso/limpiar.sql"},
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AvisoRevisionFalloIntegracionTest extends PruebaIntegracion {
  @Autowired AvisoRevisionService servicio;
  @Autowired JdbcClient jdbc;
  @MockitoBean NotificacionService notificaciones;

  @Test
  void noMarcaElEnvioSiFallaLaNotificacion() {
    jdbc.sql(
            "update inspeccion set enviada_en = timestamptz '2026-10-01T00:00:00Z' where id = '0199b700-0000-7000-8000-000000000011'")
        .update();
    doThrow(new IllegalStateException("Fallo de notificación"))
        .when(notificaciones)
        .notificar(any(), any(), any(), any());
    assertThatThrownBy(() -> servicio.avisar(Instant.parse("2026-10-27T12:30:00Z")))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.sql(
                    "select count(*) from aviso_revision where inspeccion_id = '0199b700-0000-7000-8000-000000000011'")
                .query(Long.class)
                .single())
        .isZero();
  }
}
