package com.cunoc.puentes.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.seguridad.TokenService;
import com.cunoc.puentes.notificacion.NotificacionService;
import com.cunoc.puentes.usuario.Rol;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/** Notificaciones P9, transacciones, migración y bloqueo distribuido contra PostgreSQL real. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Sql("/mantenimiento/seguimiento-rutinario.sql")
class RecordatoriosRutinariosTest extends PruebaIntegracion {
  private static final UUID PUENTE = id(1);
  private static final UUID OTRO = id(2);
  private static final LocalDate HOY = LocalDate.of(2026, 2, 28);
  @Autowired RecordatoriosRutinariosService servicio;
  @Autowired RecordatoriosRutinariosJob job;
  @Autowired SeguimientoRutinarioRepository seguimiento;
  @Autowired JdbcTemplate jdbc;
  @Autowired LockProvider candados;
  @Autowired TokenService tokens;
  @Autowired MockMvc mvc;

  @MockitoBean(name = "relojMantenimiento")
  Clock reloj;

  @MockitoSpyBean NotificacionService notificaciones;

  @BeforeEach
  void reloj() {
    when(reloj.instant()).thenReturn(Instant.parse("2026-02-28T12:00:00Z"));
    when(reloj.getZone()).thenReturn(ZoneOffset.UTC);
  }

  @AfterEach
  void limpiar() {
    reset(notificaciones);
    jdbc.update(
        "DELETE FROM aviso_rutinario_anual WHERE puente_id IN (SELECT id FROM puente WHERE municipio_codigo = '9191')");
    jdbc.update(
        "DELETE FROM notificacion WHERE usuario_id IN (SELECT id FROM usuario WHERE correo LIKE '%@anual-prueba.com')");
    jdbc.update(
        "DELETE FROM orden_mantenimiento WHERE puente_id IN (SELECT id FROM puente WHERE municipio_codigo = '9191')");
    jdbc.update("DELETE FROM puente WHERE municipio_codigo = '9191'");
    jdbc.update("DELETE FROM usuario WHERE id = ?", id(6));
    jdbc.update("DELETE FROM usuario WHERE correo LIKE '%@anual-prueba.com'");
    jdbc.update("DELETE FROM municipio WHERE codigo = '9191'");
    jdbc.update("DELETE FROM departamento WHERE codigo = '91'");
  }

  @Test
  void recuerdaAntesDelVencimientoSinCrearAceptarNiModificarOrdenes() {
    quitarProgramacion();
    var antes =
        jdbc.queryForList(
            "SELECT * FROM orden_mantenimiento WHERE puente_id = ? ORDER BY id", PUENTE);
    assertThat(servicio.recordar(PUENTE, HOY)).isEqualTo(2);
    assertThat(conteo("aviso_rutinario_anual")).isEqualTo(2);
    assertThat(conteo("notificacion")).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT mensaje FROM notificacion WHERE usuario_id = ?", String.class, id(3)))
        .contains("GT-91-9191-9901", "Puente anual A", "01/03/2026");
    assertThat(
            jdbc.queryForList(
                "SELECT * FROM orden_mantenimiento WHERE puente_id = ? ORDER BY id", PUENTE))
        .isEqualTo(antes);
  }

  @Test
  void programadaOEnEjecucionDentroDePlazoEvitaAvisarYSeRevalidaTrasDetectar() {
    assertThat(servicio.recordar(PUENTE, HOY)).isZero();
    jdbc.update("UPDATE orden_mantenimiento SET fecha_programada = NULL WHERE id = ?", id(13));
    assertThat(servicio.recordar(PUENTE, HOY)).isZero();
    quitarProgramacion();
    assertThat(seguimiento.sinProgramacion(null, HOY)).contains(PUENTE);
    jdbc.update(
        "UPDATE orden_mantenimiento SET fecha_programada = '2026-02-28' WHERE id = ?", id(13));
    assertThat(servicio.recordar(PUENTE, HOY)).isZero();
    assertThat(conteo("notificacion")).isZero();
  }

  @Test
  void registroSinEjecucionesDefineElPeriodoInclusoAntesDelAnio() {
    jdbc.update("UPDATE puente SET creado_en = '2026-01-01T00:30:00Z' WHERE id = ?", OTRO);
    assertThat(servicio.recordar(OTRO, HOY)).isEqualTo(2);
    assertThat(
            jdbc.queryForList(
                "SELECT fecha_referencia FROM aviso_rutinario_anual", LocalDate.class))
        .containsOnly(LocalDate.of(2026, 1, 1));
    assertThat(
            jdbc.queryForObject(
                "SELECT mensaje FROM notificacion WHERE usuario_id = ?", String.class, id(3)))
        .contains("01/01/2027");
  }

  @Test
  void tambienRecuerdaSiElLimiteYaVencioSinInventarUnaFechaNueva() {
    assertThat(servicio.recordar(OTRO, HOY)).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT mensaje FROM notificacion WHERE usuario_id = ?", String.class, id(3)))
        .contains("01/03/2025");
  }

  @Test
  void lecturaOCambioDeProgramacionNoRepiteElAvisoDelMismoPeriodo() {
    quitarProgramacion();
    assertThat(servicio.recordar(PUENTE, HOY)).isEqualTo(2);
    jdbc.update("UPDATE notificacion SET leida_en = now() WHERE usuario_id IN (?,?)", id(3), id(4));
    assertThat(servicio.recordar(PUENTE, HOY.plusDays(1))).isZero();
    jdbc.update(
        "UPDATE orden_mantenimiento SET fecha_programada = '2026-03-01' WHERE id = ?", id(13));
    assertThat(servicio.recordar(PUENTE, HOY)).isZero();
    quitarProgramacion();
    assertThat(servicio.recordar(PUENTE, HOY)).isZero();
    assertThat(conteo("notificacion")).isEqualTo(2);
  }

  @Test
  void nuevaEjecucionRutinariaIniciaOtroPeriodoDeRecordatorio() {
    quitarProgramacion();
    servicio.recordar(PUENTE, HOY);
    // La fecha antes posterior al límite sería válida con la nueva referencia anual.
    jdbc.update("UPDATE orden_mantenimiento SET fecha_programada = NULL WHERE id = ?", id(16));
    jdbc.update(
        """
        INSERT INTO orden_mantenimiento (id,puente_id,tipo,estado,descripcion,prioridad,fecha_ejecucion,responsable)
        VALUES (?,?,'RUTINARIO','EJECUTADA','Rutina ficticia ejecutada','MEDIA','2026-02-28','Responsable ficticio')
        """,
        id(25),
        PUENTE);
    assertThat(servicio.recordar(PUENTE, HOY)).isEqualTo(2);
    assertThat(conteo("aviso_rutinario_anual")).isEqualTo(4);
    assertThat(conteo("notificacion")).isEqualTo(4);
  }

  @Test
  void soloRolesActivosYUnDestinatarioActivadoDespuesRecibeSuPropioAviso() {
    quitarProgramacion();
    jdbc.update(
        "UPDATE usuario SET estado = 'INACTIVO', desactivado_en = now(), motivo_baja = 'Baja ficticia' WHERE id = ?",
        id(4));
    assertThat(servicio.recordar(PUENTE, HOY)).isEqualTo(1);
    jdbc.update(
        "UPDATE usuario SET estado = 'ACTIVO', desactivado_en = NULL, motivo_baja = NULL WHERE id = ?",
        id(4));
    assertThat(servicio.recordar(PUENTE, HOY)).isEqualTo(1);
    assertThat(jdbc.queryForList("SELECT usuario_id FROM aviso_rutinario_anual", UUID.class))
        .containsExactlyInAnyOrder(id(3), id(4));
  }

  @Test
  void sinDestinatariosNoSeMarcaElPeriodoComoEnviado() {
    jdbc.update("UPDATE usuario SET estado = 'PENDIENTE' WHERE id IN (?,?)", id(3), id(4));
    assertThat(servicio.recordar(OTRO, HOY)).isZero();
    assertThat(conteo("aviso_rutinario_anual")).isZero();
    jdbc.update("UPDATE usuario SET estado = 'ACTIVO' WHERE id = ?", id(3));
    assertThat(servicio.recordar(OTRO, HOY)).isEqualTo(1);
  }

  @Test
  void falloP9RevierteTodosLosAvisosDelPuenteYSusMarcasYPermiteReintento() {
    doThrow(new IllegalStateException("Fallo P9 simulado"))
        .when(notificaciones)
        .notificar(eq(id(4)), any(), any(), any());
    assertThatThrownBy(() -> servicio.recordar(OTRO, HOY))
        .isInstanceOf(IllegalStateException.class);
    assertThat(conteo("aviso_rutinario_anual")).isZero();
    assertThat(conteo("notificacion")).isZero();
    reset(notificaciones);
    assertThat(servicio.recordar(OTRO, HOY)).isEqualTo(2);
  }

  @Test
  void dosRevisionesConcurrentesNoDuplicanRecordatorios() throws Exception {
    CountDownLatch salida = new CountDownLatch(1);
    try (var ejecutor = Executors.newFixedThreadPool(2)) {
      var a =
          ejecutor.submit(
              () -> {
                salida.await();
                return servicio.recordar(OTRO, HOY);
              });
      var b =
          ejecutor.submit(
              () -> {
                salida.await();
                return servicio.recordar(OTRO, HOY);
              });
      salida.countDown();
      assertThat(a.get(10, TimeUnit.SECONDS) + b.get(10, TimeUnit.SECONDS)).isEqualTo(2);
    }
    assertThat(conteo("notificacion")).isEqualTo(2);
  }

  @Test
  void restriccionUnicaImpideOtraMarcaParaElMismoDestinatarioYPeriodo() {
    servicio.recordar(OTRO, HOY);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO aviso_rutinario_anual (id,puente_id,usuario_id,fecha_referencia) VALUES (?,?,?,'2024-03-01')",
                    id(99),
                    OTRO,
                    id(3)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(conteo("aviso_rutinario_anual")).isEqualTo(2);
  }

  @Test
  void procesaMasDeCienDestinatariosSinPerderLosUltimos() {
    jdbc.update(
        """
        INSERT INTO usuario (id,correo,nombre,contrasena_hash,rol,estado,correo_verificado_en)
        SELECT ('019a0035-0000-7000-8000-' || lpad(n::text,12,'0'))::uuid,
          'cate' || n || '@anual-prueba.com', 'Cate ficticio', 'hash-no-utilizable','CATEDRATICO','ACTIVO',now()
        FROM generate_series(100,200) n
        """);
    assertThat(servicio.recordar(OTRO, HOY)).isEqualTo(103);
    assertThat(servicio.recordar(OTRO, HOY)).isZero();
  }

  @Test
  void jobRecorreMasDeCienPuentesYLaSiguienteRevisionNoDuplica() {
    jdbc.update(
        """
        INSERT INTO puente (id,codigo,nombre,municipio_codigo,ruta,ubicacion,creado_en)
        SELECT ('019a0035-0000-7000-8000-' || lpad(n::text,12,'0'))::uuid,
          'GT-91-9191-' || n,'Puente ficticio','9191','Ruta ficticia','SRID=4326;POINT(-90.51 14.62)','2026-01-01T00:00:00Z'
        FROM generate_series(1000,1100) n
        """);
    job.ejecutar();
    assertThat(conteo("aviso_rutinario_anual")).isEqualTo(204);
    job.ejecutar();
    assertThat(conteo("notificacion")).isEqualTo(204);
  }

  @Test
  void jobAislaFallosPorPuenteYReintentaSoloLoQueNoSeConfirmo() {
    quitarProgramacion();
    doThrow(new IllegalStateException("Fallo por puente simulado"))
        .when(notificaciones)
        .notificar(any(), any(), any(), eq("/puentes/" + PUENTE));
    job.ejecutar();
    assertThat(conteo("notificacion")).isEqualTo(2);
    reset(notificaciones);
    job.ejecutar();
    assertThat(conteo("notificacion")).isEqualTo(4);
  }

  @Test
  void shedLockRealImpideLaEjecucionMientrasOtraReplicaTieneElCandado() {
    var candado =
        candados
            .lock(
                new LockConfiguration(
                    Instant.now(),
                    "recordatorios-rutinario-anual",
                    Duration.ofMinutes(1),
                    Duration.ZERO))
            .orElseThrow();
    try {
      job.ejecutar();
      assertThat(conteo("notificacion")).isZero();
    } finally {
      candado.unlock();
    }
    job.ejecutar();
    assertThat(conteo("notificacion")).isEqualTo(2);
  }

  @Test
  void p9EntregaElRecordatorioAlDestinatarioConJwtRealSinExponerloAEstudiantes() throws Exception {
    servicio.recordar(OTRO, HOY);
    mvc.perform(
            get("/api/v1/notificaciones")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + tokens.emitirAcceso(id(3), Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido.length()").value(1))
        .andExpect(jsonPath("$.contenido[0].tipo").value("RUTINARIO_SIN_PROGRAMAR"))
        .andExpect(jsonPath("$.contenido[0].enlace").value("/puentes/" + OTRO));
    mvc.perform(
            get("/api/v1/notificaciones")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + tokens.emitirAcceso(id(5), Rol.ESTUDIANTE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido.length()").value(0));
  }

  @Test
  void puenteAusenteODatosNulosNoGeneranAvisos() {
    assertThat(servicio.recordar(id(99), HOY)).isZero();
    assertThatThrownBy(() -> servicio.recordar(null, HOY))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> servicio.recordar(PUENTE, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(conteo("notificacion")).isZero();
  }

  private void quitarProgramacion() {
    jdbc.update(
        "UPDATE orden_mantenimiento SET fecha_programada = NULL WHERE id IN (?,?)", id(13), id(14));
  }

  private long conteo(String tabla) {
    String condicion =
        tabla.equals("notificacion")
            ? " WHERE usuario_id IN (SELECT id FROM usuario WHERE correo LIKE '%@anual-prueba.com')"
            : " WHERE puente_id IN (SELECT id FROM puente WHERE municipio_codigo = '9191')";
    return jdbc.queryForObject("SELECT count(*) FROM " + tabla + condicion, Long.class);
  }

  private static UUID id(int n) {
    return UUID.fromString("019a0035-0000-7000-8000-%012d".formatted(n));
  }
}
