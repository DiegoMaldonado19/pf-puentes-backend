package com.cunoc.puentes.inspeccion;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.Rol;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Sql("/inspeccion/consulta.sql")
@Sql(
    scripts = "/inspeccion/consulta-limpiar.sql",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class InspeccionConsultaTest extends PruebaIntegracion {

  static final UUID LAS_ROSAS = id(0x1);
  static final UUID EL_TIGRE = id(0x2);
  static final UUID E1 = id(0x11);
  static final UUID E2 = id(0x12);
  static final UUID C1 = id(0x13);
  static final UUID C2 = id(0x14);
  static final UUID PROFESIONAL = id(0x15);
  static final UUID ADMIN = id(0x16);
  static final UUID PUBLICADA_ANTIGUA = id(0xb1);
  static final UUID PUBLICADA_RECIENTE = id(0xb2);
  static final UUID PUBLICADA_PRO = id(0xb3);
  static final UUID BORRADOR_E1 = id(0xb4);
  static final UUID BORRADOR_E2 = id(0xb5);
  static final UUID ENVIADA = id(0xb6);
  static final UUID EN_REVISION = id(0xb7);
  static final UUID RECHAZADA = id(0xb8);
  static final UUID CAMBIOS = id(0xb9);
  static final UUID BORRADOR_PRO = id(0xba);
  static final UUID ELIMINADA = id(0xbb);
  static final UUID CORRECCION_PUB = id(0xbc);
  static final String BASE = "/api/v1/inspecciones";

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired EntityManager em;

  @Test
  void listaSoloLasMiasPaginadasSinEliminadasYSinN1() throws Exception {
    IntStream.rangeClosed(1, 142)
        .forEach(
            n ->
                jdbc.sql(
                        """
                        insert into inspeccion (id, puente_id, autor_id, formulario_version_id,
                          fecha_inspeccion, estado)
                        values (:id, :puente, :autor, :formulario, date '2026-10-01', 'BORRADOR')
                        """)
                    .param("id", id(0x200 + n))
                    .param("puente", LAS_ROSAS)
                    .param("autor", E1)
                    .param("formulario", id(0x21))
                    .update());
    Statistics estadisticas =
        em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    estadisticas.clear();

    mvc.perform(get(BASE).param("size", "150").with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(100))
        .andExpect(jsonPath("$.total_elements").value(greaterThanOrEqualTo(150)))
        .andExpect(jsonPath("$.content.length()").value(100))
        .andExpect(jsonPath("$.content[*].id").value(not(hasItem(ELIMINADA.toString()))))
        .andExpect(jsonPath("$.content[*].id").value(not(hasItem(BORRADOR_E2.toString()))));

    assertThat(estadisticas.getPrepareStatementCount()).isLessThanOrEqualTo(8);
  }

  @Test
  void filtraPorPuenteEstadoYRangoDeFechas() throws Exception {
    mvc.perform(get(BASE).param("puente_id", EL_TIGRE.toString()).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());
    mvc.perform(get(BASE).param("estado", "ENVIADA").with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(ENVIADA.toString()));
    mvc.perform(
            get(BASE)
                .param("fecha_desde", "2026-06-01")
                .param("fecha_hasta", "2026-06-30")
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(PUBLICADA_RECIENTE.toString()));
  }

  @ParameterizedTest
  @CsvSource({
    "BORRADOR, 17, ESTUDIANTE, 200",
    "BORRADOR, 19, CATEDRATICO, 200",
    "BORRADOR, 18, ESTUDIANTE, 404",
    "BORRADOR, 20, CATEDRATICO, 404",
    "BORRADOR, 21, PROFESIONAL_EXTERNO, 404",
    "BORRADOR, 22, ADMINISTRADOR, 404",
    "ENVIADA, 19, CATEDRATICO, 200",
    "EN_REVISION, 19, CATEDRATICO, 200",
    "CAMBIOS_SOLICITADOS, 17, ESTUDIANTE, 200",
    "RECHAZADA, 19, CATEDRATICO, 200",
    "ENVIADA, 18, ESTUDIANTE, 404",
    "RECHAZADA, 21, PROFESIONAL_EXTERNO, 404"
  })
  void visibilidadPorEstadoYRol(EstadoInspeccion estado, int actor, Rol rol, int respuesta)
      throws Exception {
    UUID inspeccion =
        switch (estado) {
          case BORRADOR -> BORRADOR_E1;
          case ENVIADA -> ENVIADA;
          case EN_REVISION -> EN_REVISION;
          case CAMBIOS_SOLICITADOS -> CAMBIOS;
          case RECHAZADA -> RECHAZADA;
          default -> throw new IllegalArgumentException(estado.name());
        };
    mvc.perform(get(BASE + "/" + inspeccion).with(como(rol, id(actor))))
        .andExpect(status().is(respuesta));
  }

  @Test
  void catedraticoNoVeInspeccionDeOtroCursoNiBorradorSinCurso() throws Exception {
    mvc.perform(get(BASE + "/" + BORRADOR_E2).with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isNotFound());
    mvc.perform(get(BASE + "/" + BORRADOR_PRO).with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @CsvSource({"17, ESTUDIANTE", "21, PROFESIONAL_EXTERNO", "19, CATEDRATICO", "22, ADMINISTRADOR"})
  void publicadaLaVeCualquierCuentaActiva(int actor, Rol rol) throws Exception {
    mvc.perform(get(BASE + "/" + PUBLICADA_RECIENTE).with(como(rol, id(actor))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.indice_condicion").value(80))
        .andExpect(jsonPath("$.estado_calculado").value("BUENO"))
        .andExpect(jsonPath("$.estado_confirmado").value("BUENO"));
  }

  @Test
  void visitanteRecibe401() throws Exception {
    mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    mvc.perform(get(BASE + "/" + PUBLICADA_RECIENTE)).andExpect(status().isUnauthorized());
  }

  @Test
  void listadoDelPuenteTraePublicadasYElBorradorPropio() throws Exception {
    mvc.perform(
            get("/api/v1/puentes/" + LAS_ROSAS + "/inspecciones").with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id").value(hasItem(PUBLICADA_ANTIGUA.toString())))
        .andExpect(jsonPath("$.content[*].id").value(hasItem(PUBLICADA_RECIENTE.toString())))
        .andExpect(jsonPath("$.content[*].id").value(hasItem(PUBLICADA_PRO.toString())))
        .andExpect(jsonPath("$.content[*].id").value(hasItem(BORRADOR_E1.toString())))
        .andExpect(jsonPath("$.content[*].id").value(not(hasItem(BORRADOR_E2.toString()))));
    mvc.perform(
            get("/api/v1/puentes/" + LAS_ROSAS + "/inspecciones").with(como(Rol.ESTUDIANTE, E2)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id").value(hasItem(BORRADOR_E2.toString())))
        .andExpect(jsonPath("$.content[*].id").value(not(hasItem(BORRADOR_E1.toString()))));
  }

  @Test
  void detalleMuestraFormularioGpsAcompanantesYNoLaCalificacion() throws Exception {
    mvc.perform(get(BASE + "/" + BORRADOR_E1).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.puente.nombre").value("Las Rosas"))
        .andExpect(jsonPath("$.formulario.codigo").value("SIECA-1"))
        .andExpect(jsonPath("$.estado").value("BORRADOR"))
        .andExpect(jsonPath("$.dispositivo_id").value("tablet-e1"))
        .andExpect(jsonPath("$.datos.identificacion.tipologia").value("losas"))
        .andExpect(jsonPath("$.acompanantes[0].nombre").value("Par de campo"))
        .andExpect(jsonPath("$.gps_disponible").value(true))
        .andExpect(jsonPath("$.distancia_a_puente_m").value(612.0))
        .andExpect(jsonPath("$.advertencia_ubicacion").exists())
        .andExpect(jsonPath("$.indice_condicion").doesNotExist())
        .andExpect(jsonPath("$.estado_calculado").doesNotExist())
        .andExpect(jsonPath("$.calificacion").doesNotExist())
        .andExpect(jsonPath("$.calificacion_revisor").doesNotExist());
  }

  @Test
  void sinGpsSeIndicaYLaCorreccionPublicadaMarcaSuperada() throws Exception {
    mvc.perform(get(BASE + "/" + BORRADOR_PRO).with(como(Rol.PROFESIONAL_EXTERNO, PROFESIONAL)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gps_disponible").value(false))
        .andExpect(jsonPath("$.advertencia_ubicacion").value("GPS no disponible"));
    mvc.perform(get(BASE + "/" + PUBLICADA_ANTIGUA).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.superada").value(true))
        .andExpect(jsonPath("$.superada_por").value(CORRECCION_PUB.toString()));
    mvc.perform(get(BASE + "/" + PUBLICADA_RECIENTE).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.superada").value(false));
  }

  @Test
  void historialCronologicoConLaMismaVisibilidad() throws Exception {
    mvc.perform(get(BASE + "/" + ENVIADA + "/historial").with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$[0].estado_origen").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$[0].estado_destino").value("BORRADOR"))
        .andExpect(jsonPath("$[3].estado_destino").value("CAMBIOS_SOLICITADOS"))
        .andExpect(jsonPath("$[3].motivo").value("Falta profundidad del estribo"))
        .andExpect(jsonPath("$[5].estado_destino").value("ENVIADA"));
    mvc.perform(get(BASE + "/" + ENVIADA + "/historial").with(como(Rol.ESTUDIANTE, E2)))
        .andExpect(status().isNotFound());
  }

  @Test
  void idInexistenteOEliminadoEs404() throws Exception {
    mvc.perform(get(BASE + "/" + id(0x99)).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isNotFound());
    mvc.perform(get(BASE + "/" + ELIMINADA).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isNotFound());
  }

  static UUID id(int n) {
    return UUID.fromString("0199c200-0000-7000-8000-%012x".formatted(n));
  }
}
