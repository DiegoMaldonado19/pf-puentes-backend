package com.cunoc.puentes.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@Sql("/mantenimiento/ordenes.sql")
class OrdenMantenimientoRepositoryTest extends PruebaIntegracion {

  private static final UUID PUENTE_A = UUID.fromString("019a0022-0000-7000-8000-000000000001");
  private static final UUID PUENTE_B = UUID.fromString("019a0022-0000-7000-8000-000000000002");
  private static final UUID INSPECCION = UUID.fromString("019a0022-0000-7000-8000-000000000005");
  private static final UUID CANCELADA = UUID.fromString("019a0022-0000-7000-8000-000000000010");
  private static final UUID INEXISTENTE = UUID.fromString("019a0022-0000-7000-8000-000000000099");
  private static final DatosTransicionOrden SIN_DATOS =
      new DatosTransicionOrden(null, null, 0, null);

  @Autowired OrdenMantenimientoRepository repositorio;
  @Autowired TestEntityManager em;

  @Test
  void guardaUnaOrdenConOrigenPrioridadesProgramacionYMarcasDeTiempo() {
    OrdenMantenimiento orden = nueva(PUENTE_A, TipoOrden.CORRECTIVO);
    orden.setInspeccionId(INSPECCION);
    orden.setElementoRef("elemento-de-prueba");
    orden.setPrioridadSugerida(Prioridad.MEDIA);
    orden.setPrioridad(Prioridad.ALTA);
    orden.setJustificacionPrioridad("Justificación de prueba");
    orden.setFechaProgramada(LocalDate.of(2026, 10, 25));
    UUID id = guardar(orden).getId();
    em.clear();

    OrdenMantenimiento leida = repositorio.findById(id).orElseThrow();
    em.clear();

    assertThat(id.version()).isEqualTo(7);
    assertThat(leida.getPuenteId()).isEqualTo(PUENTE_A);
    assertThat(leida.getInspeccionId()).isEqualTo(INSPECCION);
    assertThat(leida.getTipo()).isEqualTo(TipoOrden.CORRECTIVO);
    assertThat(leida.getEstado()).isEqualTo(EstadoOrden.PROPUESTA);
    assertThat(leida.getDescripcion()).isEqualTo("Intervención de prueba");
    assertThat(leida.getElementoRef()).isEqualTo("elemento-de-prueba");
    assertThat(leida.getPrioridadSugerida()).isEqualTo(Prioridad.MEDIA);
    assertThat(leida.getPrioridad()).isEqualTo(Prioridad.ALTA);
    assertThat(leida.getJustificacionPrioridad()).isEqualTo("Justificación de prueba");
    assertThat(leida.getFechaProgramada()).isEqualTo(LocalDate.of(2026, 10, 25));
    assertThat(leida.getFechaEjecucion()).isNull();
    assertThat(leida.getResponsable()).isNull();
    assertThat(leida.getMotivo()).isNull();
    assertThat(leida.getCreadoEn()).isNotNull();
    assertThat(leida.getActualizadoEn()).isNotNull();
  }

  @ParameterizedTest
  @EnumSource(TipoOrden.class)
  void conservaLosCuatroTiposSinExigirInspeccionDeOrigen(TipoOrden tipo) {
    UUID id = guardar(nueva(PUENTE_A, tipo)).getId();
    em.clear();

    assertThat(repositorio.findById(id))
        .hasValueSatisfying(
            leida -> {
              assertThat(leida.getTipo()).isEqualTo(tipo);
              assertThat(leida.getInspeccionId()).isNull();
            });
  }

  @Test
  void paginaPorPuenteSinMezclarOrdenesNiOcultarLasTerminales() {
    OrdenMantenimiento descartada = nueva(PUENTE_A, TipoOrden.CORRECTIVO);
    descartada.cambiarEstado(
        EstadoOrden.DESCARTADA, new DatosTransicionOrden(null, null, 0, "Motivo de descarte"));
    UUID idDescartada = guardar(descartada).getId();
    UUID idOtra = guardar(nueva(PUENTE_B, TipoOrden.RUTINARIO)).getId();
    em.clear();

    Page<OrdenMantenimiento> primera = repositorio.findByPuenteId(PUENTE_A, pagina(0, 1));
    em.clear();
    assertThat(primera.getTotalElements()).isEqualTo(2);
    assertThat(primera.getTotalPages()).isEqualTo(2);
    assertThat(primera.getContent())
        .singleElement()
        .satisfies(
            orden -> {
              assertThat(orden.getId()).isEqualTo(CANCELADA);
              assertThat(orden.getEstado()).isEqualTo(EstadoOrden.CANCELADA);
              assertThat(orden.getMotivo()).isEqualTo("Motivo histórico de prueba");
            });
    assertThat(repositorio.findByPuenteId(PUENTE_A, pagina(1, 1)).getContent())
        .singleElement()
        .satisfies(
            orden -> {
              assertThat(orden.getId()).isEqualTo(idDescartada);
              assertThat(orden.getEstado()).isEqualTo(EstadoOrden.DESCARTADA);
              assertThat(orden.getMotivo()).isEqualTo("Motivo de descarte");
            });
    assertThat(repositorio.findByPuenteId(PUENTE_B, pagina(0, 10)).getContent())
        .extracting(OrdenMantenimiento::getId)
        .containsExactly(idOtra);
  }

  @Test
  void paginaFueraDelRangoConservaLosTotalesYNoDevuelveOtraOrden() {
    Page<OrdenMantenimiento> pagina = repositorio.findByPuenteId(PUENTE_A, pagina(10, 1));
    assertThat(pagina.getContent()).isEmpty();
    assertThat(pagina.getTotalElements()).isEqualTo(1);
  }

  @Test
  void idInexistenteYPuenteSinOrdenesDevuelvenAusencia() {
    assertThat(repositorio.findById(INEXISTENTE)).isEmpty();
    assertThat(repositorio.findByPuenteId(PUENTE_B, pagina(0, 10))).isEmpty();
  }

  @Test
  void persisteUnaTransicionValidadaSinSetterDeEstado() {
    UUID id = guardar(nueva(PUENTE_A, TipoOrden.PREVENTIVO)).getId();
    em.clear();
    OrdenMantenimiento orden = repositorio.findById(id).orElseThrow();
    orden.setFechaProgramada(LocalDate.of(2026, 10, 25));
    orden.cambiarEstado(EstadoOrden.PROGRAMADA, SIN_DATOS);
    guardar(orden);
    em.clear();

    assertThat(repositorio.findById(id))
        .hasValueSatisfying(
            leida -> {
              assertThat(leida.getEstado()).isEqualTo(EstadoOrden.PROGRAMADA);
              assertThat(leida.getFechaProgramada()).isEqualTo(LocalDate.of(2026, 10, 25));
            });
  }

  @Test
  void persisteElAjusteJustificadoSinReemplazarLaSugerencia() {
    OrdenMantenimiento orden = nueva(PUENTE_A, TipoOrden.CORRECTIVO);
    orden.setPrioridadSugerida(Prioridad.MEDIA);
    UUID id = guardar(orden).getId();
    em.clear();

    OrdenMantenimiento existente = repositorio.findById(id).orElseThrow();
    existente.ajustarPrioridad(Prioridad.ALTA, "Se requiere atención prioritaria");
    guardar(existente);
    em.clear();

    assertThat(repositorio.findById(id))
        .hasValueSatisfying(
            leida -> {
              assertThat(leida.getPrioridadSugerida()).isEqualTo(Prioridad.MEDIA);
              assertThat(leida.getPrioridad()).isEqualTo(Prioridad.ALTA);
              assertThat(leida.getJustificacionPrioridad())
                  .isEqualTo("Se requiere atención prioritaria");
              assertThat(leida.getEstado()).isEqualTo(EstadoOrden.PROPUESTA);
            });
  }

  @Test
  void rechazarUnAjusteNoDejaCambiosParaElFlushDeUnaEntidadGestionada() {
    OrdenMantenimiento orden = nueva(PUENTE_A, TipoOrden.PREVENTIVO);
    orden.setPrioridadSugerida(Prioridad.MEDIA);
    UUID id = guardar(orden).getId();
    em.clear();
    OrdenMantenimiento existente = repositorio.findById(id).orElseThrow();

    assertThatThrownBy(() -> existente.ajustarPrioridad(Prioridad.BAJA, " \t"))
        .isInstanceOf(NegocioException.class);
    em.flush();
    em.clear();

    assertThat(repositorio.findById(id))
        .hasValueSatisfying(
            leida -> {
              assertThat(leida.getPrioridadSugerida()).isEqualTo(Prioridad.MEDIA);
              assertThat(leida.getPrioridad()).isEqualTo(Prioridad.MEDIA);
              assertThat(leida.getJustificacionPrioridad()).isNull();
            });
  }

  @Test
  void elContratoNoExponeBorradoFisicoNiFindAllSinPaginacion() {
    assertThat(Arrays.stream(OrdenMantenimientoRepository.class.getMethods()).map(Method::getName))
        .noneMatch(nombre -> nombre.startsWith("delete"))
        .doesNotContain("findAll");
  }

  @Test
  void postgresRechazaUnaOrdenSinPuente() {
    comprobarRestriccion(nueva(null, TipoOrden.RUTINARIO), "23502");
  }

  @Test
  void postgresRechazaUnPuenteInexistente() {
    comprobarRestriccion(nueva(INEXISTENTE, TipoOrden.RUTINARIO), "23503");
  }

  @Test
  void postgresRechazaUnaInspeccionDeOrigenInexistente() {
    OrdenMantenimiento orden = nueva(PUENTE_A, TipoOrden.CORRECTIVO);
    orden.setInspeccionId(INEXISTENTE);
    comprobarRestriccion(orden, "23503");
  }

  @Test
  void postgresRechazaCambiarLaPrioridadSugeridaSinJustificacion() {
    OrdenMantenimiento orden = nueva(PUENTE_A, TipoOrden.CORRECTIVO);
    orden.setPrioridadSugerida(Prioridad.BAJA);
    orden.setPrioridad(Prioridad.ALTA);
    comprobarRestriccion(orden, "23514");
  }

  private void comprobarRestriccion(OrdenMantenimiento orden, String estadoSql) {
    Throwable error = catchThrowable(() -> guardar(orden));
    assertThat(error).isNotNull();
    assertThat(NestedExceptionUtils.getMostSpecificCause(error))
        .isInstanceOfSatisfying(
            PSQLException.class, causa -> assertThat(causa.getSQLState()).isEqualTo(estadoSql));
  }

  private OrdenMantenimiento guardar(OrdenMantenimiento orden) {
    OrdenMantenimiento guardada = repositorio.save(orden);
    em.flush();
    return guardada;
  }

  private static OrdenMantenimiento nueva(UUID puente, TipoOrden tipo) {
    OrdenMantenimiento orden = new OrdenMantenimiento();
    orden.setPuenteId(puente);
    orden.setTipo(tipo);
    orden.setDescripcion("Intervención de prueba");
    orden.setPrioridad(Prioridad.MEDIA);
    return orden;
  }

  private static PageRequest pagina(int numero, int tamano) {
    return PageRequest.of(numero, tamano, Sort.by("id"));
  }
}
