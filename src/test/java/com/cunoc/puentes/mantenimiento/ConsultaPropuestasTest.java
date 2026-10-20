package com.cunoc.puentes.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.mantenimiento.dto.OrdenMantenimientoDTO;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@Import({ConsultaOrdenes.class, ValidacionOrigenOrden.class, OrdenMantenimientoMapperImpl.class})
@Sql({"/mantenimiento/ordenes.sql", "/mantenimiento/propuestas.sql"})
class ConsultaPropuestasTest extends PruebaIntegracion {

  private static final UUID PUENTE = UUID.fromString("019a0022-0000-7000-8000-000000000001");
  private static final UUID OTRO = UUID.fromString("019a0022-0000-7000-8000-000000000002");
  private static final UUID PRIMERA = UUID.fromString("019a0026-0000-7000-8000-000000000012");
  private static final UUID SEGUNDA = UUID.fromString("019a0026-0000-7000-8000-000000000011");
  private static final UUID INEXISTENTE = UUID.fromString("019a0022-0000-7000-8000-000000000099");

  @Autowired ConsultaOrdenes consulta;
  @Autowired OrdenMantenimientoRepository ordenes;
  @Autowired TestEntityManager em;
  @Autowired JdbcTemplate jdbc;

  @Test
  void paginaSoloPropuestasDelPuenteConTotalesFiltradosYOrdenEstable() {
    var primera = consulta.listarPropuestas(PUENTE, 0, 1);
    assertThat(primera.getTotalElements()).isEqualTo(2);
    assertThat(primera.getTotalPages()).isEqualTo(2);
    assertThat(primera.getContent()).extracting(OrdenMantenimientoDTO::id).containsExactly(PRIMERA);
    assertThat(consulta.listarPropuestas(PUENTE, 1, 1).getContent())
        .extracting(OrdenMantenimientoDTO::id)
        .containsExactly(SEGUNDA);
    assertThat(consulta.listarPropuestas(OTRO, 0, 100).getContent())
        .singleElement()
        .satisfies(dto -> assertThat(dto.puenteId()).isEqualTo(OTRO));
  }

  @Test
  void excluirEstadosDelPendienteNoBorraNiOcultaElHistorial() {
    assertThat(consulta.listarPropuestas(PUENTE, 0, 20).getContent())
        .allSatisfy(dto -> assertThat(dto.estado()).isEqualTo(EstadoOrden.PROPUESTA));
    assertThat(consulta.listar(PUENTE, 0, 20).getContent())
        .extracting(OrdenMantenimientoDTO::estado)
        .contains(EstadoOrden.values());
  }

  @Test
  void unaPropuestaAceptadaDejaDeContarseSinPerderLaOrden() {
    OrdenMantenimiento orden = ordenes.findById(PRIMERA).orElseThrow();
    orden.cambiarEstado(EstadoOrden.PROGRAMADA, new DatosTransicionOrden(null, null, 0, null));
    em.flush();
    em.clear();
    var pagina = consulta.listarPropuestas(PUENTE, 0, 20);
    assertThat(pagina.getTotalElements()).isEqualTo(1);
    assertThat(pagina.getContent()).extracting(OrdenMantenimientoDTO::id).containsExactly(SEGUNDA);
    assertThat(consulta.obtener(PUENTE, PRIMERA).estado()).isEqualTo(EstadoOrden.PROGRAMADA);
  }

  @Test
  void ausenciaYPaginaPosteriorConservanLosTotales() {
    var fuera = consulta.listarPropuestas(PUENTE, 10, 20);
    assertThat(fuera).isEmpty();
    assertThat(fuera.getTotalElements()).isEqualTo(2);
    jdbc.update("DELETE FROM orden_mantenimiento WHERE puente_id = ?", OTRO);
    var vacia = consulta.listarPropuestas(OTRO, 0, 20);
    assertThat(vacia).isEmpty();
    assertThat(vacia.getTotalElements()).isZero();
  }

  @ParameterizedTest
  @CsvSource({"-1,20", "0,0", "0,101", "2147483647,100"})
  void comparteLaValidacionDePaginacion(int pagina, int tamano) {
    assertThatThrownBy(() -> consulta.listarPropuestas(PUENTE, pagina, tamano))
        .isInstanceOfSatisfying(
            NegocioException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void unPuenteInexistenteNoSeConfundeConUnaBandejaVacia() {
    assertThatThrownBy(() -> consulta.listarPropuestas(INEXISTENTE, 0, 20))
        .isInstanceOfSatisfying(
            NegocioException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void leerLaBandejaNoEscribeOrdenesNiAuditoria() {
    Long auditorias = jdbc.queryForObject("SELECT count(*) FROM auditoria", Long.class);
    Long ordenesAntes = jdbc.queryForObject("SELECT count(*) FROM orden_mantenimiento", Long.class);
    consulta.listarPropuestas(PUENTE, 0, 20);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria", Long.class))
        .isEqualTo(auditorias);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM orden_mantenimiento", Long.class))
        .isEqualTo(ordenesAntes);
  }
}
