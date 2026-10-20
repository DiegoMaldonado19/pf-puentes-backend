package com.cunoc.puentes.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.mantenimiento.dto.OrdenMantenimientoDTO;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@Import({ConsultaOrdenes.class, ValidacionOrigenOrden.class, OrdenMantenimientoMapperImpl.class})
@Sql("/mantenimiento/ordenes.sql")
class ConsultaOrdenesTest extends PruebaIntegracion {

  private static final UUID PUENTE = UUID.fromString("019a0022-0000-7000-8000-000000000001");
  private static final UUID OTRO = UUID.fromString("019a0022-0000-7000-8000-000000000002");
  private static final UUID CANCELADA = UUID.fromString("019a0022-0000-7000-8000-000000000010");
  private static final UUID INEXISTENTE = UUID.fromString("019a0022-0000-7000-8000-000000000099");

  @Autowired ConsultaOrdenes consulta;
  @Autowired OrdenMantenimientoRepository ordenes;
  @Autowired TestEntityManager em;
  @Autowired JdbcTemplate jdbc;

  @Test
  void paginaDtoPorPuenteEnOrdenEstableSinMezclarOtrosPuentes() {
    UUID primera = guardar(PUENTE);
    UUID segunda = guardar(PUENTE);
    guardar(OTRO);
    em.clear();
    List<UUID> esperados =
        List.of(CANCELADA, primera, segunda).stream().sorted(Comparator.reverseOrder()).toList();

    Page<OrdenMantenimientoDTO> pagina = consulta.listar(PUENTE, 0, 2);
    em.clear();
    assertThat(pagina.getTotalElements()).isEqualTo(3);
    assertThat(pagina.getTotalPages()).isEqualTo(2);
    assertThat(pagina.getContent())
        .extracting(OrdenMantenimientoDTO::id)
        .containsExactlyElementsOf(esperados.subList(0, 2));
    assertThat(pagina.getContent()).allSatisfy(dto -> assertThat(dto.puenteId()).isEqualTo(PUENTE));
    assertThat(consulta.listar(PUENTE, 1, 2).getContent())
        .extracting(OrdenMantenimientoDTO::id)
        .containsExactly(esperados.getLast());
  }

  @Test
  void puenteExistenteSinOrdenesDevuelvePaginaVacia() {
    assertThat(consulta.listar(OTRO, 0, 20)).isEmpty();
  }

  @Test
  void paginaPosteriorALaUltimaConservaLosTotales() {
    Page<OrdenMantenimientoDTO> pagina = consulta.listar(PUENTE, 10, 20);
    assertThat(pagina.getContent()).isEmpty();
    assertThat(pagina.getTotalElements()).isEqualTo(1);
  }

  @Test
  void detalleConservaCanceladaYMotivoSinHabilitarCancelacion() {
    OrdenMantenimientoDTO dto = consulta.obtener(PUENTE, CANCELADA);
    em.clear();
    assertThat(dto.id()).isEqualTo(CANCELADA);
    assertThat(dto.estado()).isEqualTo(EstadoOrden.CANCELADA);
    assertThat(dto.motivo()).isEqualTo("Motivo histórico de prueba");
    assertThat(dto.inspeccionId()).isNull();
  }

  @Test
  void detalleDeOtroPuenteNoSeDevuelveAunqueLaOrdenExista() {
    comprobarError(HttpStatus.NOT_FOUND, () -> consulta.obtener(OTRO, CANCELADA));
  }

  @Test
  void recursosInexistentesProducen404() {
    comprobarError(HttpStatus.NOT_FOUND, () -> consulta.listar(INEXISTENTE, 0, 20));
    comprobarError(HttpStatus.NOT_FOUND, () -> consulta.obtener(PUENTE, INEXISTENTE));
    comprobarError(HttpStatus.NOT_FOUND, () -> consulta.obtener(INEXISTENTE, CANCELADA));
  }

  @ParameterizedTest
  @CsvSource({"-1,20", "0,0", "0,-1", "0,101", "2147483647,100"})
  void rechazaPaginasInvalidasIncluidoDesbordamientoDelDesplazamiento(int pagina, int tamano) {
    comprobarError(HttpStatus.BAD_REQUEST, () -> consulta.listar(PUENTE, pagina, tamano));
  }

  @Test
  void permiteElLimiteDeCien() {
    assertThat(consulta.listar(PUENTE, 0, 100).getSize()).isEqualTo(100);
  }

  @Test
  void exigeIdentificadoresInternosPresentes() {
    assertThatIllegalArgumentException().isThrownBy(() -> consulta.listar(null, 0, 20));
    assertThatIllegalArgumentException().isThrownBy(() -> consulta.obtener(null, CANCELADA));
    assertThatIllegalArgumentException().isThrownBy(() -> consulta.obtener(PUENTE, null));
  }

  @Test
  void lasConsultasNoEscribenOrdenesNiAuditoriaDeMutaciones() {
    Long antes = jdbc.queryForObject("SELECT count(*) FROM auditoria", Long.class);
    consulta.listar(PUENTE, 0, 20);
    consulta.obtener(PUENTE, CANCELADA);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria", Long.class)).isEqualTo(antes);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM orden_mantenimiento", Long.class))
        .isEqualTo(1L);
  }

  private UUID guardar(UUID puente) {
    OrdenMantenimiento orden = new OrdenMantenimiento();
    orden.setPuenteId(puente);
    orden.setTipo(TipoOrden.RUTINARIO);
    orden.setPrioridad(Prioridad.MEDIA);
    orden.setDescripcion("Intervención de prueba");
    UUID id = ordenes.save(orden).getId();
    em.flush();
    return id;
  }

  private static void comprobarError(HttpStatus estado, Runnable operacion) {
    assertThatThrownBy(operacion::run)
        .isInstanceOfSatisfying(
            NegocioException.class, error -> assertThat(error.getStatusCode()).isEqualTo(estado));
  }
}
