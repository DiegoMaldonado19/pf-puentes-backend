package com.cunoc.puentes.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.common.NegocioException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrdenMantenimientoTest {

  private static final DatosTransicionOrden SIN_DATOS =
      new DatosTransicionOrden(null, null, 0, null);

  @Test
  void nuevaOrdenIniciaEnPropuestaYNoExponeUnSetterDeEstado() {
    OrdenMantenimiento orden = new OrdenMantenimiento();

    assertThat(orden.getEstado()).isEqualTo(EstadoOrden.PROPUESTA);
    assertThat(Arrays.stream(OrdenMantenimiento.class.getDeclaredMethods()).map(Method::getName))
        .doesNotContain("setEstado");
  }

  @Test
  void cicloHastaEjecutadaGuardaLosDatosDelCierreYConservaLaIntervencion() {
    OrdenMantenimiento orden = new OrdenMantenimiento();
    UUID puente = UUID.fromString("019a0019-0000-7000-8000-000000000001");
    LocalDate programada = LocalDate.of(2026, 10, 19);
    LocalDate ejecutada = LocalDate.of(2026, 10, 20);
    orden.setPuenteId(puente);
    orden.setDescripcion("Intervención de prueba");
    orden.setTipo(TipoOrden.CORRECTIVO);
    orden.setPrioridadSugerida(Prioridad.ALTA);
    orden.setPrioridad(Prioridad.ALTA);
    orden.setFechaProgramada(programada);

    orden.cambiarEstado(EstadoOrden.PROGRAMADA, SIN_DATOS);
    assertThat(orden.getEstado()).isEqualTo(EstadoOrden.PROGRAMADA);
    orden.cambiarEstado(EstadoOrden.EN_EJECUCION, SIN_DATOS);
    assertThat(orden.getEstado()).isEqualTo(EstadoOrden.EN_EJECUCION);
    orden.cambiarEstado(
        EstadoOrden.EJECUTADA, new DatosTransicionOrden(ejecutada, "Responsable", 1, null));

    assertThat(orden.getEstado()).isEqualTo(EstadoOrden.EJECUTADA);
    assertThat(orden.getFechaEjecucion()).isEqualTo(ejecutada);
    assertThat(orden.getResponsable()).isEqualTo("Responsable");
    assertThat(orden.getPuenteId()).isEqualTo(puente);
    assertThat(orden.getDescripcion()).isEqualTo("Intervención de prueba");
    assertThat(orden.getTipo()).isEqualTo(TipoOrden.CORRECTIVO);
    assertThat(orden.getPrioridadSugerida()).isEqualTo(Prioridad.ALTA);
    assertThat(orden.getPrioridad()).isEqualTo(Prioridad.ALTA);
    assertThat(orden.getFechaProgramada()).isEqualTo(programada);
  }

  @Test
  void descartarGuardaElMotivoYSigueConservandoLaOrden() {
    OrdenMantenimiento orden = new OrdenMantenimiento();
    orden.setDescripcion("Intervención de prueba");
    orden.cambiarEstado(
        EstadoOrden.DESCARTADA,
        new DatosTransicionOrden(null, null, 0, "La intervención no corresponde"));

    assertThat(orden.getEstado()).isEqualTo(EstadoOrden.DESCARTADA);
    assertThat(orden.getMotivo()).isEqualTo("La intervención no corresponde");
    assertThat(orden.getDescripcion()).isEqualTo("Intervención de prueba");
    assertThat(orden.getFechaEjecucion()).isNull();
  }

  @Test
  void rechazoDeCierreNoDejaCambiosParcialesEnLaEntidad() {
    OrdenMantenimiento orden = new OrdenMantenimiento();
    orden.cambiarEstado(EstadoOrden.PROGRAMADA, SIN_DATOS);
    orden.cambiarEstado(EstadoOrden.EN_EJECUCION, SIN_DATOS);

    assertThatThrownBy(
            () ->
                orden.cambiarEstado(
                    EstadoOrden.EJECUTADA,
                    new DatosTransicionOrden(LocalDate.of(2026, 10, 19), "Responsable", 0, null)))
        .isInstanceOf(NegocioException.class);
    assertThat(orden.getEstado()).isEqualTo(EstadoOrden.EN_EJECUCION);
    assertThat(orden.getFechaEjecucion()).isNull();
    assertThat(orden.getResponsable()).isNull();
  }

  @Test
  void rechazoDeDescarteNoCambiaEstadoNiMotivo() {
    OrdenMantenimiento orden = new OrdenMantenimiento();

    assertThatThrownBy(() -> orden.cambiarEstado(EstadoOrden.DESCARTADA, SIN_DATOS))
        .isInstanceOf(NegocioException.class);
    assertThat(orden.getEstado()).isEqualTo(EstadoOrden.PROPUESTA);
    assertThat(orden.getMotivo()).isNull();
  }

  @Test
  void cancelarConservaFechaYMotivoSinPermitirReapertura() {
    var orden = new OrdenMantenimiento();
    var fecha = LocalDate.of(2026, 10, 25);
    orden.setFechaProgramada(fecha);
    orden.cambiarEstado(EstadoOrden.PROGRAMADA, SIN_DATOS);
    assertThatThrownBy(() -> orden.cambiarEstado(EstadoOrden.CANCELADA, SIN_DATOS))
        .isInstanceOf(NegocioException.class);
    assertThat(orden.getEstado()).isEqualTo(EstadoOrden.PROGRAMADA);
    assertThat(orden.getMotivo()).isNull();
    orden.cambiarEstado(
        EstadoOrden.CANCELADA,
        new DatosTransicionOrden(null, null, 0, "  Cancelación documentada  "));
    assertThat(orden.getMotivo()).isEqualTo("  Cancelación documentada  ");
    assertThat(orden.getFechaProgramada()).isEqualTo(fecha);
    assertThatThrownBy(() -> orden.cambiarEstado(EstadoOrden.PROGRAMADA, SIN_DATOS))
        .isInstanceOf(NegocioException.class);
    assertThat(orden.getEstado()).isEqualTo(EstadoOrden.CANCELADA);
  }

  @Test
  void servicioDeMotivoNoEsUnaPuertaParaCambiosDeEstadoArbitrarios() {
    for (var destino :
        new EstadoOrden[] {
          EstadoOrden.PROPUESTA,
          EstadoOrden.PROGRAMADA,
          EstadoOrden.EN_EJECUCION,
          EstadoOrden.EJECUTADA,
          null
        }) {
      assertThatThrownBy(
              () ->
                  new DecisionConMotivoOrdenService()
                      .decidir(new OrdenMantenimiento(), destino, "motivo"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void saltoDeEstadoNoGuardaLosDatosDeCierreAunqueEstenCompletos() {
    OrdenMantenimiento orden = new OrdenMantenimiento();

    assertThatThrownBy(
            () ->
                orden.cambiarEstado(
                    EstadoOrden.EJECUTADA,
                    new DatosTransicionOrden(LocalDate.of(2026, 10, 19), "Responsable", 1, null)))
        .isInstanceOf(NegocioException.class);
    assertThat(orden.getEstado()).isEqualTo(EstadoOrden.PROPUESTA);
    assertThat(orden.getFechaEjecucion()).isNull();
    assertThat(orden.getResponsable()).isNull();
  }
}
