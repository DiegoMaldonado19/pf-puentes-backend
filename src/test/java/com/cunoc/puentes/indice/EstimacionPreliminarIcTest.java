package com.cunoc.puentes.indice;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.Rol;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql({"/indice/servicio-pesos.sql", "/indice/creacion-juegos.sql"})
class EstimacionPreliminarIcTest extends PruebaIntegracion {

  private static final UUID FORM = UUID.fromString("019a0018-0000-7000-8000-000000000001");
  private static final UUID FORM_SIN_PESOS =
      UUID.fromString("019a0018-0000-7000-8000-000000000002");
  private static final UUID JUEGO_1 = UUID.fromString("019a0040-0000-7000-8000-000000000011");
  private static final UUID JUEGO_2 = UUID.fromString("019a0040-0000-7000-8000-000000000015");
  private static final UUID PUENTE = UUID.fromString("019a0040-0000-7000-8000-000000000001");
  private static final UUID AUTOR = UUID.fromString("019a0018-0000-7000-8000-000000000003");
  private static final UUID INSPECCION_PUBLICADA =
      UUID.fromString("019a0040-0000-7000-8000-000000000021");
  private static final UUID INSPECCION_BORRADOR =
      UUID.fromString("019a0040-0000-7000-8000-000000000022");
  private static final UUID INSPECCION_CRITICA =
      UUID.fromString("019a0040-0000-7000-8000-000000000023");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired EstimacionPreliminarIcService servicio;

  @BeforeEach
  void prepararDatos() {
    jdbc.update(
        "INSERT INTO peso_elemento(id, version_pesos_id, elemento_ref, peso) VALUES (gen_random_uuid(), ?, 'elemento.b', 3)",
        JUEGO_1);

    jdbc.update(
        """
        INSERT INTO version_pesos(id, formulario_version_id, acta, vigente_desde)
        VALUES (?, ?, 'Acta v2', now() + interval '1 day')
        """,
        JUEGO_2,
        FORM);
    jdbc.update(
        """
        INSERT INTO peso_elemento(id, version_pesos_id, elemento_ref, peso) VALUES
          (gen_random_uuid(), ?, 'elemento.a', 1),
          (gen_random_uuid(), ?, 'elemento.b', 1)
        """,
        JUEGO_2,
        JUEGO_2);

    jdbc.update(
        """
        INSERT INTO inspeccion(id, puente_id, autor_id, formulario_version_id, estado, fecha_inspeccion, datos, ubicacion_inicio, dispositivo_id)
        VALUES (?, ?, ?, ?, 'BORRADOR', '2026-10-02', '{"a": 0, "b": 0}'::jsonb, 'SRID=4326;POINT(-90.5 14.6)', 'disp-test')
        """,
        INSPECCION_BORRADOR,
        PUENTE,
        AUTOR,
        FORM);

    jdbc.update(
        """
        INSERT INTO inspeccion(id, puente_id, autor_id, formulario_version_id, estado, fecha_inspeccion, datos, ubicacion_inicio, dispositivo_id, anulacion)
        VALUES (?, ?, ?, ?, 'BORRADOR', '2026-10-02', '{"a": 0, "b": 0}'::jsonb, 'SRID=4326;POINT(-90.5 14.6)', 'disp-test', 'SOCAVACION')
        """,
        INSPECCION_CRITICA,
        PUENTE,
        AUTOR,
        FORM);
  }

  @AfterEach
  void limpiarContexto() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Estima IC preliminar por formulario con respuestas y pesos explícitos (RN-IC-07)")
  void estimaIcPreliminarPorFormulario() throws Exception {
    // a=0 (peso 1), b=2 (peso 1): suma daños = 2, daño máx = 8 -> IC = 75.00 BUENO
    String body =
        """
        {
          "version_pesos_id": "%s",
          "datos": { "a": 0, "b": 2 }
        }
        """
            .formatted(JUEGO_2);

    mvc.perform(
            post("/api/v1/formularios/{formularioId}/ic-preliminar", FORM)
                .with(como(Rol.ESTUDIANTE, AUTOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preliminar").value(true))
        .andExpect(jsonPath("$.rotulo").value("IC PRELIMINAR"))
        .andExpect(jsonPath("$.indice_condicion").value(75.00))
        .andExpect(jsonPath("$.estado_calculado").value("BUENO"))
        .andExpect(jsonPath("$.anulaciones").isEmpty())
        .andExpect(jsonPath("$.version_pesos_id").value(JUEGO_2.toString()))
        .andExpect(jsonPath("$.advertencia").isNotEmpty());
  }

  @Test
  @DisplayName("Estima IC preliminar seleccionando automáticamente el juego más reciente")
  void estimaIcPreliminarAutoSeleccionandoJuego() throws Exception {
    // Sin enviar version_pesos_id: usa JUEGO_2 (más reciente creado)
    String body =
        """
        {
          "datos": { "a": 0, "b": 0 }
        }
        """;

    mvc.perform(
            post("/api/v1/formularios/{formularioId}/ic-preliminar", FORM)
                .with(como(Rol.CATEDRATICO, AUTOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preliminar").value(true))
        .andExpect(jsonPath("$.indice_condicion").value(100.00))
        .andExpect(jsonPath("$.estado_calculado").value("BUENO"))
        .andExpect(jsonPath("$.version_pesos_id").value(JUEGO_2.toString()));
  }

  @Test
  @DisplayName("Estima IC preliminar con condición crítica forzando estado Malo (RN-IC-05)")
  void estimaIcPreliminarConCondicionCritica() throws Exception {
    String body =
        """
        {
          "version_pesos_id": "%s",
          "datos": { "a": 0, "b": 0 },
          "condiciones_criticas": {
            "socavacion_con_cimentacion_expuesta_en_estribo_o_pila": true
          }
        }
        """
            .formatted(JUEGO_2);

    mvc.perform(
            post("/api/v1/formularios/{formularioId}/ic-preliminar", FORM)
                .with(como(Rol.ADMINISTRADOR, AUTOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.indice_condicion").value(100.00))
        .andExpect(jsonPath("$.estado_calculado").value("MALO"))
        .andExpect(jsonPath("$.anulaciones[0]").value("SOCAVACION"));
  }

  @Test
  @DisplayName("Rechaza estimación por formulario si la versión no tiene pesos configurados")
  void rechazaEstimacionSinPesos() throws Exception {
    String body =
        """
        {
          "datos": { "a": 0 }
        }
        """;

    mvc.perform(
            post("/api/v1/formularios/{formularioId}/ic-preliminar", FORM_SIN_PESOS)
                .with(como(Rol.ESTUDIANTE, AUTOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.type").value("https://sgp.cunoc.usac.edu.gt/errores/sin-juegos-de-pesos"));
  }

  @Test
  @DisplayName("Estima IC preliminar para borrador de inspección sin modificar registros")
  void estimaIcPreliminarParaBorradorInspeccion() throws Exception {
    var borradorAntes =
        jdbc.queryForMap("SELECT * FROM inspeccion WHERE id = ?", INSPECCION_BORRADOR);
    var puenteAntes = jdbc.queryForMap("SELECT * FROM puente WHERE id = ?", PUENTE);

    mvc.perform(
            post("/api/v1/inspecciones/{inspeccionId}/ic-preliminar", INSPECCION_BORRADOR)
                .with(como(Rol.ESTUDIANTE, AUTOR))
                .param("versionPesosId", JUEGO_2.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preliminar").value(true))
        .andExpect(jsonPath("$.indice_condicion").value(100.00))
        .andExpect(jsonPath("$.estado_calculado").value("BUENO"));

    var borradorDespues =
        jdbc.queryForMap("SELECT * FROM inspeccion WHERE id = ?", INSPECCION_BORRADOR);
    var puenteDespues = jdbc.queryForMap("SELECT * FROM puente WHERE id = ?", PUENTE);

    assertThat(borradorDespues).isEqualTo(borradorAntes);
    assertThat(puenteDespues).isEqualTo(puenteAntes);
  }

  @Test
  @DisplayName("Estima IC preliminar para borrador detectando anulación crítica guardada")
  void estimaIcPreliminarParaBorradorConAnulacion() throws Exception {
    mvc.perform(
            post("/api/v1/inspecciones/{inspeccionId}/ic-preliminar", INSPECCION_CRITICA)
                .with(como(Rol.CATEDRATICO, AUTOR))
                .param("versionPesosId", JUEGO_2.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.indice_condicion").value(100.00))
        .andExpect(jsonPath("$.estado_calculado").value("MALO"))
        .andExpect(jsonPath("$.anulaciones[0]").value("SOCAVACION"));
  }

  @Test
  @DisplayName("Rechaza estimación preliminar en inspección que ya está PUBLICADA (409)")
  void rechazaEstimacionEnInspeccionPublicada() throws Exception {
    mvc.perform(
            post("/api/v1/inspecciones/{inspeccionId}/ic-preliminar", INSPECCION_PUBLICADA)
                .with(como(Rol.ADMINISTRADOR, AUTOR))
                .param("versionPesosId", JUEGO_2.toString()))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type")
                .value("https://sgp.cunoc.usac.edu.gt/errores/inspeccion-ya-publicada"));
  }

  @Test
  @DisplayName("Rechaza estimación para inspección inexistente (404)")
  void rechazaInspeccionInexistente() throws Exception {
    mvc.perform(
            post("/api/v1/inspecciones/{inspeccionId}/ic-preliminar", UUID.randomUUID())
                .with(como(Rol.ESTUDIANTE, AUTOR))
                .param("versionPesosId", JUEGO_2.toString()))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type")
                .value("https://sgp.cunoc.usac.edu.gt/errores/inspeccion-no-encontrada"));
  }

  @Test
  @DisplayName("Valida confirmación coincidente sin justificación (RN-IC-06)")
  void validaConfirmacionCoincidente() throws Exception {
    String body =
        """
        {
          "estado_calculado": "BUENO",
          "estado_confirmado": "BUENO"
        }
        """;

    mvc.perform(
            post("/api/v1/inspecciones/confirmacion-estado/validacion")
                .with(como(Rol.CATEDRATICO, AUTOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado_calculado").value("BUENO"))
        .andExpect(jsonPath("$.estado_confirmado").value("BUENO"))
        .andExpect(jsonPath("$.justificacion_estado").doesNotExist())
        .andExpect(jsonPath("$.tiene_discrepancia").value(false))
        .andExpect(jsonPath("$.es_elevacion_de_anulacion_critica").value(false))
        .andExpect(jsonPath("$.estado_efectivo").value("BUENO"));
  }

  @Test
  @DisplayName("Valida confirmación discrepante con justificación y detecta elevación")
  void validaConfirmacionDiscrepanteConJustificacion() throws Exception {
    String body =
        """
        {
          "estado_calculado": "MALO",
          "estado_confirmado": "REGULAR",
          "justificacion_estado": "  Falla puntual contenida por reforzamiento preventivo  ",
          "anulacion": "PERDIDA_SECCION"
        }
        """;

    mvc.perform(
            post("/api/v1/inspecciones/confirmacion-estado/validacion")
                .with(como(Rol.CATEDRATICO, AUTOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado_calculado").value("MALO"))
        .andExpect(jsonPath("$.estado_confirmado").value("REGULAR"))
        .andExpect(
            jsonPath("$.justificacion_estado")
                .value("Falla puntual contenida por reforzamiento preventivo"))
        .andExpect(jsonPath("$.tiene_discrepancia").value(true))
        .andExpect(jsonPath("$.es_elevacion_de_anulacion_critica").value(true))
        .andExpect(jsonPath("$.estado_efectivo").value("REGULAR"));
  }

  @Test
  @DisplayName("Rechaza confirmación discrepante sin justificación (400 Bad Request)")
  void rechazaConfirmacionDiscrepanteSinJustificacion() throws Exception {
    String body =
        """
        {
          "estado_calculado": "BUENO",
          "estado_confirmado": "MALO",
          "justificacion_estado": "    "
        }
        """;

    mvc.perform(
            post("/api/v1/inspecciones/confirmacion-estado/validacion")
                .with(como(Rol.CATEDRATICO, AUTOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type")
                .value("https://sgp.cunoc.usac.edu.gt/errores/confirmacion-estado-invalida"));
  }

  @Test
  @DisplayName("Exige autenticación para acceder a estimación y confirmación (401)")
  void exigeAutenticacion() throws Exception {
    mvc.perform(
            post("/api/v1/formularios/{formularioId}/ic-preliminar", FORM)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"datos\": {}}"))
        .andExpect(status().isUnauthorized());

    mvc.perform(post("/api/v1/inspecciones/{inspeccionId}/ic-preliminar", INSPECCION_BORRADOR))
        .andExpect(status().isUnauthorized());

    mvc.perform(
            post("/api/v1/inspecciones/confirmacion-estado/validacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado_calculado\":\"BUENO\",\"estado_confirmado\":\"BUENO\"}"))
        .andExpect(status().isUnauthorized());
  }
}
