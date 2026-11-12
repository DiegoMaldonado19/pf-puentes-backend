package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.mantenimiento.dto.OrdenGlobalDTO;
import com.cunoc.puentes.mantenimiento.dto.PaginaOrdenesGlobalDTO;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * B9: listado de órdenes de todos los puentes con filtros opcionales. Cruza con {@code puente} por
 * SQL, igual que el resto de consultas entre módulos (ADR 0010); todo valor de filtro viaja como
 * parámetro.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
public class ConsultaOrdenesGlobalService {

  static final int TAMANO_MAXIMO = 100;

  private static final String DESDE =
      """
      FROM orden_mantenimiento o JOIN puente p ON p.id = o.puente_id
      """;

  private final JdbcTemplate jdbc;

  public ConsultaOrdenesGlobalService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public PaginaOrdenesGlobalDTO listar(FiltrosOrdenes filtros, int pagina, int tamano) {
    if (pagina < 0
        || tamano < 1
        || tamano > TAMANO_MAXIMO
        || (long) pagina * tamano > Integer.MAX_VALUE) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "paginacion-invalida",
          "Paginación inválida",
          "La página debe ser mayor o igual que 0 y el tamaño estar entre 1 y 100.");
    }
    FiltrosOrdenes activos = filtros != null ? filtros : new FiltrosOrdenes(null, null, null, null);
    List<Object> argumentos = new ArrayList<>();
    String condiciones = condiciones(activos, argumentos);

    Long total =
        jdbc.queryForObject(
            "SELECT count(*) " + DESDE + condiciones, Long.class, argumentos.toArray());
    long totalElementos = total != null ? total : 0L;

    List<Object> paginacion = new ArrayList<>(argumentos);
    paginacion.add(tamano);
    paginacion.add((long) pagina * tamano);
    List<OrdenGlobalDTO> contenido =
        jdbc.query(
            """
            SELECT o.id, o.puente_id, p.codigo, p.nombre, o.tipo, o.estado, o.prioridad,
                   o.descripcion, o.fecha_programada, o.creado_en
            """
                + DESDE
                + condiciones
                + " ORDER BY o.creado_en DESC, o.id DESC LIMIT ? OFFSET ?",
            (fila, n) -> aOrden(fila),
            paginacion.toArray());

    int paginas = (int) Math.ceil((double) totalElementos / tamano);
    return new PaginaOrdenesGlobalDTO(contenido, pagina, tamano, totalElementos, paginas);
  }

  private static String condiciones(FiltrosOrdenes filtros, List<Object> argumentos) {
    List<String> clausulas = new ArrayList<>();
    if (filtros.estado() != null) {
      clausulas.add("o.estado = ?");
      argumentos.add(filtros.estado().name());
    }
    if (filtros.tipo() != null) {
      clausulas.add("o.tipo = ?");
      argumentos.add(filtros.tipo().name());
    }
    if (filtros.prioridad() != null) {
      clausulas.add("o.prioridad = ?");
      argumentos.add(filtros.prioridad().name());
    }
    if (filtros.texto() != null && !filtros.texto().isBlank()) {
      String patron = "%" + escaparLike(filtros.texto().trim()) + "%";
      clausulas.add("(o.descripcion ILIKE ? OR p.nombre ILIKE ? OR p.codigo ILIKE ?)");
      argumentos.add(patron);
      argumentos.add(patron);
      argumentos.add(patron);
    }
    return clausulas.isEmpty() ? "" : "WHERE " + String.join(" AND ", clausulas) + " ";
  }

  private static String escaparLike(String texto) {
    return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static OrdenGlobalDTO aOrden(ResultSet fila) throws SQLException {
    Date programada = fila.getDate("fecha_programada");
    Instant creado = fila.getTimestamp("creado_en").toInstant();
    return new OrdenGlobalDTO(
        fila.getObject("id", UUID.class),
        fila.getObject("puente_id", UUID.class),
        fila.getString("codigo"),
        fila.getString("nombre"),
        TipoOrden.valueOf(fila.getString("tipo")),
        EstadoOrden.valueOf(fila.getString("estado")),
        Prioridad.valueOf(fila.getString("prioridad")),
        fila.getString("descripcion"),
        programada != null ? programada.toLocalDate() : null,
        creado);
  }

  /** Filtros del listado; cada uno es opcional. */
  public record FiltrosOrdenes(
      EstadoOrden estado, TipoOrden tipo, Prioridad prioridad, String texto) {}
}
