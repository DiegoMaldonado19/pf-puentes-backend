package com.cunoc.puentes.mantenimiento;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.indice.EstadoCondicion;
import com.cunoc.puentes.inspeccion.InspeccionPublicada;
import com.cunoc.puentes.mantenimiento.dto.OrdenMantenimientoDTO;
import com.cunoc.puentes.usuario.Rol;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql({"/indice/servicio-pesos.sql", "/indice/creacion-juegos.sql"})
class GeneracionPropuestasMantenimientoTest extends PruebaIntegracion {

  private static final UUID FORM_SIECA_V1 = UUID.fromString("0192b000-0000-7000-8000-000000000001");
  private static final UUID PUENTE = UUID.fromString("019a0040-0000-7000-8000-000000000001");
  private static final UUID AUTOR = UUID.fromString("019a0018-0000-7000-8000-000000000003");
  private static final UUID JUEGO_SIECA = UUID.fromString("019a0040-0000-7000-8000-000000000050");
  private static final UUID INSPECCION_PUB =
      UUID.fromString("019a0040-0000-7000-8000-000000000060");
  private static final UUID INSPECCION_BORRADOR =
      UUID.fromString("019a0040-0000-7000-8000-000000000061");

  @Autowired private ApplicationEventPublisher eventos;
  @Autowired private GeneracionPropuestasMantenimientoService servicio;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MockMvc mvc;

  @BeforeEach
  void prepararDatosSieca() {
    jdbc.update(
        "INSERT INTO version_pesos(id, formulario_version_id, acta) VALUES (?, ?, ?)",
        JUEGO_SIECA,
        FORM_SIECA_V1,
        "Acta SIECA prueba");
    jdbc.update(
        """
        INSERT INTO peso_elemento(id, version_pesos_id, elemento_ref, peso) VALUES
          ('019a0040-0000-7000-8000-000000000051', ?, 'subestructura.estribo_entrada.grietas', 1.0),
          ('019a0040-0000-7000-8000-000000000052', ?, 'subestructura.estribo_entrada.socavacion', 3.0),
          ('019a0040-0000-7000-8000-000000000053', ?, 'superestructura.vigas.deformacion', 2.5),
          ('019a0040-0000-7000-8000-000000000054', ?, 'elementos_no_estructurales.barandas.danos', 1.0)
        """,
        JUEGO_SIECA,
        JUEGO_SIECA,
        JUEGO_SIECA,
        JUEGO_SIECA);

    String datosPublicada =
        """
        {
          "subestructura": {
            "estribo_entrada_grietas": 1,
            "estribo_entrada_socavacion": 3,
            "estribo_salida_grietas": 0
          },
          "superestructura": {
            "vigas_deformacion": 3,
            "losa_agrietamiento": 4
          },
          "elementos_no_estructurales": {
            "barandas_danos": 2
          }
        }
        """;

    jdbc.update(
        """
        INSERT INTO inspeccion(
          id, puente_id, autor_id, formulario_version_id, estado, fecha_inspeccion, datos,
          publicada_en, version_pesos_id, indice_condicion, estado_calculado, anulacion, estado_confirmado
        ) VALUES (?, ?, ?, ?, 'PUBLICADA', '2026-10-20', ?::jsonb, now(), ?, 62.50, 'MALO', 'SOCAVACION', 'MALO')
        """,
        INSPECCION_PUB,
        PUENTE,
        AUTOR,
        FORM_SIECA_V1,
        datosPublicada,
        JUEGO_SIECA);

    jdbc.update(
        """
        INSERT INTO inspeccion(
          id, puente_id, autor_id, formulario_version_id, estado, fecha_inspeccion, datos
        ) VALUES (?, ?, ?, ?, 'BORRADOR', '2026-10-21', ?::jsonb)
        """,
        INSPECCION_BORRADOR,
        PUENTE,
        AUTOR,
        FORM_SIECA_V1,
        datosPublicada);
  }

  @Test
  @DisplayName(
      "Genera propuestas de mantenimiento desde daños publicados (RN-MTO-02/03/06) y las expone en bandeja")
  void generaPropuestasAlPublicarInspeccionSinAceptarlasAutomaticamente() throws Exception {
    InspeccionPublicada evento =
        new InspeccionPublicada(
            INSPECCION_PUB,
            PUENTE,
            new BigDecimal("62.50"),
            EstadoCondicion.MALO,
            LocalDate.of(2026, 10, 20));

    eventos.publishEvent(evento);

    Integer totalPropuestas =
        jdbc.queryForObject(
            "SELECT count(*) FROM orden_mantenimiento WHERE puente_id = ? AND inspeccion_id = ?",
            Integer.class,
            PUENTE,
            INSPECCION_PUB);
    // Calificaciones: grietas=1 (ignora), salida_grietas=0 (ignora),
    // barandas=2 (PREVENTIVO), vigas_deformacion=3 (CORRECTIVO),
    // estribo_entrada_socavacion=3 con anulación crítica (EMERGENCIA), losa_agrietamiento=4
    // (EMERGENCIA)
    assertThat(totalPropuestas).isEqualTo(4);

    Integer totalAuditadas =
        jdbc.queryForObject(
            "SELECT count(*) FROM auditoria WHERE entidad = 'orden_mantenimiento' AND accion = 'CREAR'",
            Integer.class);
    assertThat(totalAuditadas).isEqualTo(4);

    mvc.perform(
            get("/api/v1/puentes/{puenteId}/ordenes-mantenimiento/propuestas", PUENTE)
                .with(como(Rol.CATEDRATICO, AUTOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(4))
        .andExpect(jsonPath("$.content[0].estado").value("PROPUESTA"))
        .andExpect(jsonPath("$.content[0].inspeccion_id").value(INSPECCION_PUB.toString()))
        .andExpect(jsonPath("$.content[0].fecha_programada").isEmpty());
  }

  @Test
  @DisplayName(
      "Es idempotente: reprocesar el mismo evento InspeccionPublicada no duplica propuestas")
  void reprocesarEventoNoDuplicaPropuestas() {
    InspeccionPublicada evento =
        new InspeccionPublicada(
            INSPECCION_PUB,
            PUENTE,
            new BigDecimal("62.50"),
            EstadoCondicion.MALO,
            LocalDate.of(2026, 10, 20));

    List<OrdenMantenimientoDTO> primera = servicio.generarDesdeInspeccionPublicada(evento);
    List<OrdenMantenimientoDTO> segunda = servicio.generarDesdeInspeccionPublicada(evento);

    assertThat(primera).hasSize(4);
    assertThat(segunda).isEmpty();

    Integer totalPropuestas =
        jdbc.queryForObject(
            "SELECT count(*) FROM orden_mantenimiento WHERE inspeccion_id = ?",
            Integer.class,
            INSPECCION_PUB);
    assertThat(totalPropuestas).isEqualTo(4);
  }

  @Test
  @DisplayName(
      "Los borradores no disparan propuestas de mantenimiento (criterio 4 de HU-P8-MTO-02)")
  void borradorNoGeneraPropuestas() {
    InspeccionPublicada eventoBorrador =
        new InspeccionPublicada(
            INSPECCION_BORRADOR,
            PUENTE,
            new BigDecimal("62.50"),
            EstadoCondicion.MALO,
            LocalDate.of(2026, 10, 21));

    assertThatThrownBy(() -> servicio.generarDesdeInspeccionPublicada(eventoBorrador))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no publicadas no generan propuestas");
  }

  @Test
  @DisplayName("Asegura propuesta rutinaria anual de forma idempotente sin duplicarla (RN-MTO-07)")
  void aseguraPropuestaRutinariaAnualIdempotente() {
    var primera = servicio.asegurarPropuestaRutinariaAnual(PUENTE);
    var segunda = servicio.asegurarPropuestaRutinariaAnual(PUENTE);

    assertThat(primera).isPresent();
    assertThat(primera.get().tipo()).isEqualTo(TipoOrden.RUTINARIO);
    assertThat(primera.get().estado()).isEqualTo(EstadoOrden.PROPUESTA);
    assertThat(primera.get().prioridadSugerida()).isEqualTo(Prioridad.MEDIA);
    assertThat(segunda).isEmpty();
  }
}
