package com.cunoc.puentes.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.mantenimiento.dto.PaginaOrdenesGlobalDTO;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ConsultaOrdenesGlobalServiceTest {

  @Mock private JdbcTemplate jdbc;

  @Test
  @DisplayName("Una página fuera de rango o un tamaño mayor que 100 responde 400")
  void paginacionInvalidaResponde400() {
    var servicio = new ConsultaOrdenesGlobalService(jdbc);

    assertThatThrownBy(() -> servicio.listar(null, -1, 20))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            error ->
                assertThat(((NegocioException) error).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
    assertThatThrownBy(() -> servicio.listar(null, 0, 101)).isInstanceOf(NegocioException.class);
    assertThatThrownBy(() -> servicio.listar(null, 0, 0)).isInstanceOf(NegocioException.class);
  }

  @Test
  @DisplayName("Sin resultados, la página tiene cero elementos y cero páginas")
  void sinResultadosNoTienePaginas() {
    when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
    when(jdbc.query(
            anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of());

    PaginaOrdenesGlobalDTO pagina = new ConsultaOrdenesGlobalService(jdbc).listar(null, 0, 20);

    assertThat(pagina.totalElements()).isZero();
    assertThat(pagina.totalPages()).isZero();
    assertThat(pagina.content()).isEmpty();
  }

  @Test
  @DisplayName("El texto de búsqueda se escapa para ILIKE y viaja como parámetro, no en el SQL")
  void textoDeBusquedaSeEscapaComoParametro() {
    var filtros =
        new ConsultaOrdenesGlobalService.FiltrosOrdenes(
            EstadoOrden.PROGRAMADA, TipoOrden.CORRECTIVO, Prioridad.ALTA, "50%_x");
    when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
    when(jdbc.query(
            anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of());

    new ConsultaOrdenesGlobalService(jdbc).listar(filtros, 0, 20);

    String patron = "%50\\%\\_x%";
    verify(jdbc)
        .queryForObject(
            org.mockito.ArgumentMatchers.contains("o.estado = ?"),
            eq(Long.class),
            eq("PROGRAMADA"),
            eq("CORRECTIVO"),
            eq("ALTA"),
            eq(patron),
            eq(patron),
            eq(patron));
  }
}
