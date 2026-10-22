package com.cunoc.puentes.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
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
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Sql("/mantenimiento/alertas-inspeccion.sql")
class AlertasInspeccionTest extends PruebaIntegracion {
  private static final UUID PUENTE = id(1);
  private static final UUID OTRO = id(2);
  private static final Instant LIMITE = Instant.parse("2025-10-21T12:00:00Z");
  private static final Instant VENCIDO = LIMITE.plusNanos(1000);
  @Autowired AlertasInspeccionService servicio;
  @Autowired AlertasInspeccionRepository repositorio;
  @Autowired AlertasInspeccionJob job;
  @Autowired LockProvider candados;
  @Autowired JdbcTemplate jdbc;
  @Autowired TokenService tokens;
  @Autowired MockMvc mvc;
  @MockitoSpyBean NotificacionService notificaciones;

  @AfterEach
  void limpiar() {
    reset(notificaciones);
    jdbc.update(
        "DELETE FROM aviso_inspeccion_vencida WHERE puente_id IN (SELECT id FROM puente WHERE municipio_codigo = '9292')");
    jdbc.update(
        "DELETE FROM notificacion WHERE usuario_id IN (SELECT id FROM usuario WHERE correo LIKE '%@alertas-prueba.com')");
    jdbc.update("DELETE FROM inspeccion WHERE puente_id IN (?, ?)", PUENTE, OTRO);
    jdbc.update("DELETE FROM formulario_version WHERE id = ?", id(7));
    jdbc.update("DELETE FROM puente WHERE municipio_codigo = '9292'");
    jdbc.update("DELETE FROM usuario WHERE id = ?", id(6));
    jdbc.update("DELETE FROM usuario WHERE correo LIKE '%@alertas-prueba.com'");
    jdbc.update("DELETE FROM municipio WHERE codigo = '9292'");
    jdbc.update("DELETE FROM departamento WHERE codigo = '92'");
  }

  @Test
  void limiteExactoNoAlertaPeroUnMicrosegundoDespuesSi() {
    assertThat(servicio.alertar(PUENTE, LIMITE.minusNanos(1000))).isZero();
    assertThat(servicio.alertar(PUENTE, LIMITE)).isZero();
    assertThat(servicio.alertar(PUENTE, VENCIDO)).isEqualTo(2);
    assertThat(conteo("notificacion")).isEqualTo(2);
    assertThat(conteo("aviso_inspeccion_vencida")).isEqualTo(2);
  }

  @Test
  void nuncaPublicadaUsaRegistroYNoFechaDelBorrador() {
    insertarInspeccion(9, OTRO, "BORRADOR", "2025-10-20T12:00:00Z");
    assertThat(servicio.alertar(OTRO, LIMITE)).isZero();
    assertThat(servicio.alertar(OTRO, VENCIDO)).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM aviso_inspeccion_vencida WHERE inspeccion_id IS NULL",
                Long.class))
        .isEqualTo(2);
  }

  @Test
  void publicacionYNoVisitaReiniciaElPlazoYSeleccionaLaMasReciente() {
    insertarInspeccion(9, PUENTE, "PUBLICADA", "2025-10-20T12:00:00Z");
    assertThat(servicio.alertar(PUENTE, VENCIDO)).isZero();
    assertThat(servicio.alertar(PUENTE, Instant.parse("2026-10-20T12:00:00Z"))).isZero();
    assertThat(servicio.alertar(PUENTE, Instant.parse("2026-10-20T12:00:00.000001Z"))).isEqualTo(2);
    assertThat(jdbc.queryForList("SELECT inspeccion_id FROM aviso_inspeccion_vencida", UUID.class))
        .containsOnly(id(9));
  }

  @Test
  void estadosNoPublicadosNoReinicianElPlazo() {
    for (String estado :
        new String[] {"BORRADOR", "ENVIADA", "EN_REVISION", "CAMBIOS_SOLICITADOS", "RECHAZADA"}) {
      insertarInspeccion(10 + estado.length(), PUENTE, estado, "2025-10-20T12:00:00Z");
    }
    assertThat(servicio.alertar(PUENTE, VENCIDO)).isEqualTo(2);
  }

  @Test
  void doceMesesDeCalendarioRespetanAnioBisiesto() {
    jdbc.update("UPDATE inspeccion SET publicada_en = '2024-02-29T12:00:00Z' WHERE id = ?", id(8));
    assertThat(servicio.alertar(PUENTE, Instant.parse("2025-02-28T12:00:00Z"))).isZero();
    assertThat(servicio.alertar(PUENTE, Instant.parse("2025-02-28T12:00:00.000001Z"))).isEqualTo(2);
  }

  @Test
  void noRepiteAlDiaSiguienteNiPorLeerElAviso() {
    assertThat(servicio.alertar(PUENTE, VENCIDO)).isEqualTo(2);
    jdbc.update(
        "UPDATE notificacion SET leida_en = now() WHERE usuario_id IN (?, ?)", id(3), id(4));
    assertThat(servicio.alertar(PUENTE, VENCIDO.plus(Duration.ofDays(1)))).isZero();
    assertThat(conteo("notificacion")).isEqualTo(2);
  }

  @Test
  void nuevaPublicacionPermiteUnAvisoCuandoVuelvaAVencer() {
    assertThat(servicio.alertar(PUENTE, VENCIDO)).isEqualTo(2);
    insertarInspeccion(9, PUENTE, "PUBLICADA", "2025-11-01T12:00:00Z");
    assertThat(servicio.alertar(PUENTE, Instant.parse("2026-11-01T12:00:00Z"))).isZero();
    assertThat(servicio.alertar(PUENTE, Instant.parse("2026-11-01T12:00:00.000001Z"))).isEqualTo(2);
    assertThat(conteo("notificacion")).isEqualTo(4);
  }

  @Test
  void rolesYCuentasVigentesSinPerderDestinatariosQueSeActivenDespues() {
    jdbc.update(
        "UPDATE usuario SET estado = 'INACTIVO', desactivado_en = now(), motivo_baja = 'Baja ficticia de prueba' WHERE id = ?",
        id(4));
    assertThat(servicio.alertar(PUENTE, VENCIDO)).isEqualTo(1);
    jdbc.update(
        "UPDATE usuario SET estado = 'ACTIVO', desactivado_en = NULL, motivo_baja = NULL WHERE id = ?",
        id(4));
    assertThat(servicio.alertar(PUENTE, VENCIDO.plusSeconds(1))).isEqualTo(1);
    assertThat(jdbc.queryForList("SELECT usuario_id FROM aviso_inspeccion_vencida", UUID.class))
        .containsExactlyInAnyOrder(id(3), id(4));
  }

  @Test
  void sinDestinatariosNoMarcaElPuenteComoNotificado() {
    jdbc.update(
        "UPDATE usuario SET estado = 'INACTIVO', desactivado_en = now(), motivo_baja = 'Baja ficticia de prueba' WHERE id IN (?, ?)",
        id(3),
        id(4));
    assertThat(servicio.alertar(PUENTE, VENCIDO)).isZero();
    assertThat(conteo("aviso_inspeccion_vencida")).isZero();
    jdbc.update(
        "UPDATE usuario SET estado = 'ACTIVO', desactivado_en = NULL, motivo_baja = NULL WHERE id = ?",
        id(3));
    assertThat(servicio.alertar(PUENTE, VENCIDO)).isEqualTo(1);
  }

  @Test
  void falloDeP9RevierteAvisosYMarcasYPermiteReintento() {
    doThrow(new IllegalStateException("Fallo simulado P9"))
        .when(notificaciones)
        .notificar(eq(id(4)), any(), any(), any());
    assertThatThrownBy(() -> servicio.alertar(PUENTE, VENCIDO))
        .isInstanceOf(IllegalStateException.class);
    assertThat(conteo("notificacion")).isZero();
    assertThat(conteo("aviso_inspeccion_vencida")).isZero();
    reset(notificaciones);
    assertThat(servicio.alertar(PUENTE, VENCIDO)).isEqualTo(2);
  }

  @Test
  void dosEjecucionesConcurrentesNoDuplican() throws Exception {
    CountDownLatch salida = new CountDownLatch(1);
    try (var ejecutor = Executors.newFixedThreadPool(2)) {
      var a =
          ejecutor.submit(
              () -> {
                salida.await();
                return servicio.alertar(PUENTE, VENCIDO);
              });
      var b =
          ejecutor.submit(
              () -> {
                salida.await();
                return servicio.alertar(PUENTE, VENCIDO);
              });
      salida.countDown();
      assertThat(a.get(10, TimeUnit.SECONDS) + b.get(10, TimeUnit.SECONDS)).isEqualTo(2);
    }
    assertThat(conteo("notificacion")).isEqualTo(2);
  }

  @Test
  void paginacionDeDestinatariosNoOmiteLosQueSuperanCien() {
    jdbc.update(
        """
        INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol, estado, correo_verificado_en)
        SELECT ('019a0034-0000-7000-8000-' || lpad(n::text,12,'0'))::uuid,
        'cate' || n || '@alertas-prueba.com', 'Catedrático ficticio', 'hash-no-utilizable', 'CATEDRATICO', 'ACTIVO', now()
        FROM generate_series(100,200) n
        """);
    assertThat(servicio.alertar(PUENTE, VENCIDO)).isEqualTo(103);
    assertThat(servicio.alertar(PUENTE, VENCIDO.plusSeconds(1))).isZero();
  }

  @Test
  void jobConRelojControladoRecorreMasDeCienPuentes() {
    jdbc.update(
        """
        INSERT INTO puente (id,codigo,nombre,municipio_codigo,ruta,ubicacion,creado_en)
        SELECT ('019a0034-0000-7000-8000-' || lpad(n::text,12,'0'))::uuid,
        'GT-92-9292-' || n, 'Puente ficticio', '9292', 'Ruta ficticia', 'SRID=4326;POINT(-90.51 14.62)', '2024-10-21T12:00:00Z'
        FROM generate_series(1000,1100) n
        """);
    new AlertasInspeccionJob(repositorio, servicio, Clock.fixed(VENCIDO, ZoneOffset.UTC))
        .ejecutar();
    assertThat(conteo("aviso_inspeccion_vencida")).isEqualTo(206);
  }

  @Test
  void jobAislaElPuenteQueFallaYLoReintentaEnLaProximaCorrida() {
    doThrow(new IllegalStateException("Fallo simulado por puente"))
        .when(notificaciones)
        .notificar(any(), any(), any(), eq("/puentes/" + PUENTE));
    var controlado =
        new AlertasInspeccionJob(repositorio, servicio, Clock.fixed(VENCIDO, ZoneOffset.UTC));
    controlado.ejecutar();
    assertThat(conteo("notificacion")).isEqualTo(2);
    reset(notificaciones);
    controlado.ejecutar();
    assertThat(conteo("notificacion")).isEqualTo(4);
  }

  @Test
  void bloqueoDistribuidoRealImpideEjecutarElJobMientrasOtraReplicaLoTiene() {
    var candado =
        candados
            .lock(
                new LockConfiguration(
                    Instant.now(),
                    "alertas-inspeccion-vencida",
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
    assertThat(conteo("notificacion")).isEqualTo(4);
  }

  @Test
  void p9EntregaLaAlertaAlDestinatarioAutenticadoConEnlaceReal() throws Exception {
    servicio.alertar(PUENTE, VENCIDO);
    mvc.perform(
            get("/api/v1/notificaciones")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + tokens.emitirAcceso(id(3), Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido.length()").value(1))
        .andExpect(jsonPath("$.contenido[0].tipo").value("INSPECCION_VENCIDA"))
        .andExpect(jsonPath("$.contenido[0].enlace").value("/puentes/" + PUENTE));
    mvc.perform(
            get("/api/v1/notificaciones")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + tokens.emitirAcceso(id(5), Rol.ESTUDIANTE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido.length()").value(0));
  }

  @Test
  void puenteAusenteOQueDejoDeEstarVencidoEntreConsultaYProcesoNoGeneraAviso() {
    assertThat(servicio.alertar(id(99), VENCIDO)).isZero();
    assertThat(repositorio.siguientes(null, VENCIDO)).contains(PUENTE, OTRO);
    insertarInspeccion(9, PUENTE, "PUBLICADA", "2025-10-21T12:00:00Z");
    assertThat(servicio.alertar(PUENTE, VENCIDO)).isZero();
    assertThatThrownBy(() -> servicio.alertar(null, VENCIDO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> servicio.alertar(PUENTE, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private long conteo(String tabla) {
    String condicion =
        tabla.equals("notificacion")
            ? " WHERE usuario_id IN (SELECT id FROM usuario WHERE correo LIKE '%@alertas-prueba.com')"
            : " WHERE puente_id IN (SELECT id FROM puente WHERE municipio_codigo = '9292')";
    return jdbc.queryForObject("SELECT count(*) FROM " + tabla + condicion, Long.class);
  }

  private void insertarInspeccion(int numero, UUID puente, String estado, String fecha) {
    boolean publicada = estado.equals("PUBLICADA");
    jdbc.update(
        """
        INSERT INTO inspeccion (id,puente_id,autor_id,formulario_version_id,estado,fecha_inspeccion,
          publicada_en,indice_condicion,estado_calculado,estado_confirmado)
        VALUES (?,?,?,?,?,'2023-10-19',?,?,?,?)
        """,
        id(numero),
        puente,
        id(5),
        id(7),
        estado,
        publicada ? java.sql.Timestamp.from(Instant.parse(fecha)) : null,
        publicada ? 75 : null,
        publicada ? "BUENO" : null,
        publicada ? "BUENO" : null);
  }

  private static UUID id(int numero) {
    return UUID.fromString("019a0034-0000-7000-8000-%012d".formatted(numero));
  }
}
