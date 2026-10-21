package com.cunoc.puentes.puente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.puente.dto.CrearPuenteDTO;
import com.cunoc.puentes.puente.dto.EditarPuenteDTO;
import com.cunoc.puentes.puente.dto.PuenteDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Sin transacción de prueba: commits, bloqueos y auditoría reales. Usa Almolonga (0913) y Flores
 * (1701) para no cruzarse con los fixtures de otros módulos.
 */
@SpringBootTest
class PuenteIntegracionTest extends PruebaIntegracion {

  // Almolonga, Quetzaltenango
  private static final double LAT = 14.8126;
  private static final double LON = -91.4950;
  // ~0.0009° de latitud son unos 100 m
  private static final double A_50_M = 0.00045;
  private static final double A_150_M = 0.00135;

  @Autowired PuenteService servicio;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void limpiar() {
    jdbc.update(
        "DELETE FROM auditoria WHERE entidad = 'puente' AND entidad_id IN"
            + " (SELECT id FROM puente WHERE municipio_codigo IN ('0913', '1701'))");
    jdbc.update("DELETE FROM puente WHERE municipio_codigo IN ('0913', '1701')");
    jdbc.update("DELETE FROM correlativo_puente WHERE municipio_codigo IN ('0913', '1701')");
  }

  static CrearPuenteDTO alta(String nombre, double lat, double lon, boolean confirmar) {
    return new CrearPuenteDTO(nombre, "09", "0913", "RN-1", lat, lon, null, confirmar);
  }

  @Test
  void asignaCorrelativosPorMunicipioYAuditaLaCreacion() {
    PuenteDTO primero = servicio.crear(alta("Primero", LAT, LON, false));
    PuenteDTO segundo = servicio.crear(alta("Segundo", LAT + 0.01, LON, false));
    PuenteDTO enPeten =
        servicio.crear(
            new CrearPuenteDTO("Flores", "17", "1701", "CA-13", 16.9256, -89.8936, 1990, false));

    assertThat(primero.codigo()).isEqualTo("GT-09-0913-0001");
    assertThat(segundo.codigo()).isEqualTo("GT-09-0913-0002");
    assertThat(enPeten.codigo()).isEqualTo("GT-17-1701-0001");
    assertThat(enPeten.utm().zona()).isEqualTo(16);
    assertThat(primero.utm().zona()).isEqualTo(15);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad = 'puente' AND accion = 'CREAR'"
                    + " AND entidad_id = ?",
                Integer.class,
                primero.id()))
        .isEqualTo(1);
  }

  @Test
  void dosAltasSimultaneasNuncaRecibenElMismoCodigo() throws Exception {
    int hilos = 8;
    CountDownLatch salida = new CountDownLatch(1);
    List<Future<PuenteDTO>> resultados = new ArrayList<>();
    try (var ejecutor = Executors.newFixedThreadPool(hilos)) {
      for (int i = 0; i < hilos; i++) {
        double lat = LAT + i * 0.01;
        Callable<PuenteDTO> alta =
            () -> {
              salida.await();
              return servicio.crear(alta("Concurrente", lat, LON, true));
            };
        resultados.add(ejecutor.submit(alta));
      }
      salida.countDown();
      List<String> codigos = new ArrayList<>();
      for (Future<PuenteDTO> resultado : resultados) {
        codigos.add(resultado.get().codigo());
      }

      assertThat(codigos)
          .doesNotHaveDuplicates()
          .containsExactlyInAnyOrder(
              "GT-09-0913-0001",
              "GT-09-0913-0002",
              "GT-09-0913-0003",
              "GT-09-0913-0004",
              "GT-09-0913-0005",
              "GT-09-0913-0006",
              "GT-09-0913-0007",
              "GT-09-0913-0008");
    }
  }

  @Test
  void unAltaFallidaNoConsumeElCorrelativo() {
    servicio.crear(alta("Original", LAT, LON, false));

    assertThatThrownBy(() -> servicio.crear(alta("Duplicado", LAT + A_50_M, LON, false)))
        .isInstanceOf(NegocioException.class);

    assertThat(servicio.crear(alta("Lejano", LAT + 0.01, LON, false)).codigo())
        .isEqualTo("GT-09-0913-0002");
  }

  @Test
  void detectaPuentesAMenosDe100MetrosSobreGeography() {
    PuenteDTO original = servicio.crear(alta("Original", LAT, LON, false));

    assertThat(servicio.cercanos(LAT + A_50_M, LON, null))
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.id()).isEqualTo(original.id());
              assertThat(c.distanciaMetros()).isBetween(45.0, 55.0);
            });
    assertThat(servicio.cercanos(LAT + A_150_M, LON, null)).isEmpty();
    assertThat(servicio.cercanos(LAT, LON, original.id())).isEmpty();
  }

  @Test
  void elDuplicadoConfirmadoSeCreaYLaEdicionNoSeDetectaASiMisma() {
    PuenteDTO original = servicio.crear(alta("Original", LAT, LON, false));
    PuenteDTO confirmado = servicio.crear(alta("Confirmado", LAT + A_50_M, LON, true));

    PuenteDTO editado =
        servicio.editar(
            original.id(), new EditarPuenteDTO("Original editado", "RN-1", LAT, LON, 1950, true));

    assertThat(confirmado.codigo()).isEqualTo("GT-09-0913-0002");
    assertThat(editado.nombre()).isEqualTo("Original editado");
    assertThat(editado.codigo()).isEqualTo(original.codigo());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad = 'puente' AND accion = 'MODIFICAR'"
                    + " AND entidad_id = ?",
                Integer.class,
                original.id()))
        .isEqualTo(1);
  }

  @Test
  void filtraPorUbicacionEstadoYTextoLiteral() {
    servicio.crear(alta("Puente 100% nuevo", LAT, LON, false));
    servicio.crear(alta("Puente viejo", LAT + 0.01, LON, false));
    jdbc.update(
        "UPDATE puente SET estado_actual = 'MALO', indice_condicion_actual = 30,"
            + " fecha_ultima_inspeccion = DATE '2026-10-01' WHERE nombre = 'Puente viejo'");

    assertThat(servicio.listar("09", "0913", null, null, false, 0, 20).totalElementos())
        .isEqualTo(2);
    assertThat(servicio.listar("17", null, null, null, false, 0, 20).contenido())
        .noneMatch(p -> p.municipio().codigo().equals("0913"));
    assertThat(
            servicio.listar(null, "0913", EstadoFiltro.SIN_EVALUAR, null, false, 0, 20).contenido())
        .extracting(PuenteDTO::nombre)
        .containsExactly("Puente 100% nuevo");
    assertThat(servicio.listar(null, "0913", EstadoFiltro.MALO, null, false, 0, 20).contenido())
        .extracting(PuenteDTO::nombre)
        .containsExactly("Puente viejo");
    assertThat(servicio.listar(null, "0913", null, "100%", false, 0, 20).contenido())
        .extracting(PuenteDTO::nombre)
        .containsExactly("Puente 100% nuevo");
    assertThat(servicio.listar(null, "0913", null, "gt-09-0913", false, 0, 20).totalElementos())
        .isEqualTo(2);
  }
}
