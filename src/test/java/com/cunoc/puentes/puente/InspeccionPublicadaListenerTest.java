package com.cunoc.puentes.puente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.indice.EstadoCondicion;
import com.cunoc.puentes.inspeccion.InspeccionPublicada;
import com.cunoc.puentes.puente.dto.CrearPuenteDTO;
import com.cunoc.puentes.puente.dto.PuenteDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Simula la publicación de P5 (contrato 4): el evento sale dentro de su transacción. Usa Huitán
 * (0915) para no cruzarse con otros fixtures.
 */
@SpringBootTest
class InspeccionPublicadaListenerTest extends PruebaIntegracion {

  private static final UUID INSPECCION = UUID.fromString("019a0040-0000-7000-8000-000000000001");

  @Autowired ApplicationEventPublisher eventos;
  @Autowired PlatformTransactionManager gestor;
  @Autowired PuenteService puentes;
  @Autowired JdbcTemplate jdbc;

  PuenteDTO puente;

  @BeforeEach
  void crearPuente() {
    puente =
        puentes.crear(
            new CrearPuenteDTO("Huitán", "09", "0915", "RD-QUE-5", 15.07, -91.64, 1970, null));
  }

  @AfterEach
  void limpiar() {
    jdbc.update(
        "DELETE FROM auditoria WHERE entidad = 'puente' AND entidad_id IN"
            + " (SELECT id FROM puente WHERE municipio_codigo = '0915')");
    jdbc.update("DELETE FROM puente WHERE municipio_codigo = '0915'");
    jdbc.update("DELETE FROM correlativo_puente WHERE municipio_codigo = '0915'");
  }

  void publicar(InspeccionPublicada evento) {
    new TransactionTemplate(gestor).executeWithoutResult(estado -> eventos.publishEvent(evento));
  }

  InspeccionPublicada evento(EstadoCondicion estado, String ic, LocalDate fecha) {
    return new InspeccionPublicada(INSPECCION, puente.id(), new BigDecimal(ic), estado, fecha);
  }

  @Test
  void laPrimeraPublicacionDejaDeSerSinEvaluarYQuedaAuditada() {
    assertThat(puentes.detalle(puente.id(), false).estadoActual()).isNull(); // RN-INV-10

    publicar(evento(EstadoCondicion.BUENO, "85.50", LocalDate.of(2026, 10, 15)));

    PuenteDTO actualizado = puentes.detalle(puente.id(), false);
    assertThat(actualizado.estadoActual()).isEqualTo(EstadoCondicion.BUENO);
    assertThat(actualizado.indiceCondicionActual()).isEqualByComparingTo("85.50");
    assertThat(actualizado.fechaUltimaInspeccion()).isEqualTo(LocalDate.of(2026, 10, 15));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad = 'puente'"
                    + " AND accion = 'CAMBIAR_ESTADO' AND entidad_id = ?",
                Integer.class,
                puente.id()))
        .isEqualTo(1);
    assertThat(
            puentes.listar(null, "0915", EstadoFiltro.BUENO, null, false, 0, 20).totalElementos())
        .isEqualTo(1);
  }

  @Test
  void mandaLaInspeccionMasRecienteAunqueSePubliqueDespues() {
    publicar(evento(EstadoCondicion.MALO, "30.00", LocalDate.of(2026, 10, 15)));
    // Inspección del 01/10 que se sincronizó y publicó tarde: no pisa a la del 15/10
    publicar(evento(EstadoCondicion.BUENO, "90.00", LocalDate.of(2026, 10, 1)));

    assertThat(puentes.detalle(puente.id(), false).estadoActual()).isEqualTo(EstadoCondicion.MALO);

    publicar(evento(EstadoCondicion.REGULAR, "60.00", LocalDate.of(2026, 10, 20)));

    PuenteDTO actual = puentes.detalle(puente.id(), false);
    assertThat(actual.estadoActual()).isEqualTo(EstadoCondicion.REGULAR);
    assertThat(actual.fechaUltimaInspeccion()).isEqualTo(LocalDate.of(2026, 10, 20));
  }

  @Test
  void unPuenteInexistenteRevierteLaPublicacionCompleta() {
    InspeccionPublicada huerfano =
        new InspeccionPublicada(
            INSPECCION,
            UUID.fromString("019a0040-0000-7000-8000-0000000000ff"),
            new BigDecimal("50"),
            EstadoCondicion.REGULAR,
            LocalDate.of(2026, 10, 15));

    assertThatThrownBy(
            () ->
                new TransactionTemplate(gestor)
                    .executeWithoutResult(
                        estado -> {
                          // Lo que P5 escribió antes de publicar se revierte con el evento
                          jdbc.update(
                              "UPDATE puente SET nombre = 'Cambiado' WHERE id = ?", puente.id());
                          eventos.publishEvent(huerfano);
                        }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(puentes.detalle(puente.id(), false).nombre()).isEqualTo("Huitán");
  }

  @Test
  void unEventoIncompletoSeRechaza() {
    assertThatThrownBy(() -> publicar(evento(null, "50", LocalDate.of(2026, 10, 15))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(puentes.detalle(puente.id(), false).estadoActual()).isNull();
  }

  @Test
  void exigeLaTransaccionDeLaPublicacion() {
    assertThatThrownBy(
            () ->
                eventos.publishEvent(
                    evento(EstadoCondicion.BUENO, "80", LocalDate.of(2026, 10, 15))))
        .isInstanceOf(IllegalTransactionStateException.class);
  }
}
