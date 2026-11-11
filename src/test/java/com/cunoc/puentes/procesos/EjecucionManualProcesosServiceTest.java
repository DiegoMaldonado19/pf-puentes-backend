package com.cunoc.puentes.procesos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.RegistroManualAuditoria;
import com.cunoc.puentes.mantenimiento.AlertasInspeccionJob;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class EjecucionManualProcesosServiceTest {

  private static final Instant AHORA = Instant.parse("2026-11-11T12:00:00Z");

  @Mock private LockProvider bloqueos;
  @Mock private AlertasInspeccionJob alertas;
  @Mock private RegistroManualAuditoria auditoria;
  @Mock private SimpleLock candado;

  private EjecucionManualProcesosService servicio;

  @BeforeEach
  void setUp() {
    servicio =
        new EjecucionManualProcesosService(
            bloqueos, alertas, auditoria, Clock.fixed(AHORA, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("Un nombre fuera del catálogo de procesos manuales responde 404")
  void procesoDesconocidoResponde404() {
    assertThatThrownBy(() -> servicio.ejecutar("no-existe"))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            error ->
                assertThat(((NegocioException) error).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
    verifyNoInteractions(bloqueos, alertas);
  }

  @Test
  @DisplayName("Si la programación ya tiene el bloqueo, responde 409 y no ejecuta nada")
  void bloqueoTomadoResponde409SinEjecutar() {
    when(bloqueos.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> servicio.ejecutar("alertas-inspeccion"))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            error ->
                assertThat(((NegocioException) error).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT));
    verify(alertas, never()).ejecutarSinBloqueo();
  }

  @Test
  @DisplayName("Ejecuta con el bloqueo compartido con la programación y lo libera al terminar")
  void ejecutaConElMismoBloqueoYLoLibera() {
    when(bloqueos.lock(any(LockConfiguration.class))).thenReturn(Optional.of(candado));

    var resultado = servicio.ejecutar("alertas-inspeccion");

    ArgumentCaptor<LockConfiguration> configuracion =
        ArgumentCaptor.forClass(LockConfiguration.class);
    verify(bloqueos).lock(configuracion.capture());
    assertThat(configuracion.getValue().getName()).isEqualTo("alertas-inspeccion-vencida");
    verify(alertas).ejecutarSinBloqueo();
    verify(candado).unlock();
    verify(auditoria)
        .registrar(eq(Acciones.EJECUTAR_PROCESO), eq("proceso"), any(UUID.class), any());
    assertThat(resultado.nombre()).isEqualTo("alertas-inspeccion");
    assertThat(resultado.iniciadoEn()).isEqualTo(AHORA);
    assertThat(resultado.terminadoEn()).isEqualTo(AHORA);
  }

  @Test
  @DisplayName("El bloqueo se libera aunque la tarea falle")
  void liberaElBloqueoAunqueFalleLaTarea() {
    when(bloqueos.lock(any(LockConfiguration.class))).thenReturn(Optional.of(candado));
    doThrow(new IllegalStateException("fallo simulado")).when(alertas).ejecutarSinBloqueo();

    assertThatThrownBy(() -> servicio.ejecutar("alertas-inspeccion"))
        .isInstanceOf(IllegalStateException.class);
    verify(candado).unlock();
  }

  @Test
  @DisplayName("El identificador del proceso es estable entre ejecuciones")
  void identificadorEsEstable() {
    when(bloqueos.lock(any(LockConfiguration.class))).thenReturn(Optional.of(candado));

    var primera = servicio.ejecutar("alertas-inspeccion");
    var segunda = servicio.ejecutar("alertas-inspeccion");

    assertThat(primera.id()).isEqualTo(segunda.id()).isNotNull();
  }
}
