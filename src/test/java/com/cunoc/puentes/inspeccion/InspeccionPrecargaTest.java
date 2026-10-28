package com.cunoc.puentes.inspeccion;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.Rol;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/inspeccion/consulta.sql", "/inspeccion/borrador.sql"})
@Sql(
    scripts = "/inspeccion/consulta-limpiar.sql",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class InspeccionPrecargaTest extends PruebaIntegracion {

  static final UUID LAS_ROSAS = InspeccionConsultaTest.LAS_ROSAS;
  static final UUID EL_TIGRE = InspeccionConsultaTest.EL_TIGRE;
  static final UUID EL_SAUCE = InspeccionConsultaTest.id(0x3);
  static final UUID SIN_ANIO = InspeccionConsultaTest.id(0x5);
  static final UUID E1 = InspeccionConsultaTest.E1;
  static final UUID E2 = InspeccionConsultaTest.E2;
  static final UUID C1 = InspeccionConsultaTest.C1;
  static final UUID PROFESIONAL = InspeccionConsultaTest.PROFESIONAL;
  static final UUID BORRADOR = InspeccionConsultaTest.BORRADOR_E1;
  static final UUID ENVIADA = InspeccionConsultaTest.ENVIADA;
  static final UUID PUBLICADA_RECIENTE = InspeccionConsultaTest.PUBLICADA_RECIENTE;
  static final UUID CORRECCION = InspeccionConsultaTest.id(0xbd);
  static final UUID TIGRE_NUEVA = InspeccionConsultaTest.id(0xbf);

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;

  @Test
  void precargaDeLaUltimaPublicadaSinDanos() throws Exception {
    mvc.perform(
            get("/api/v1/inspecciones/" + BORRADOR + "/datos-permanentes")
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.origen_inspeccion_id").value(PUBLICADA_RECIENTE.toString()))
        .andExpect(jsonPath("$.datos.identificacion.tipologia").value("losas"))
        .andExpect(jsonPath("$.datos.identificacion.material_principal").value("acero"))
        .andExpect(jsonPath("$.datos.identificacion.numero_tramos").value(3))
        .andExpect(jsonPath("$.datos.subestructura").doesNotExist())
        .andExpect(jsonPath("$.anio_construccion").value(1998));
  }

  @Test
  void empatePorFechaUsaLaPublicadaMasRecienteYOmiteEnviadas() throws Exception {
    mvc.perform(
            get("/api/v1/puentes/" + EL_TIGRE + "/datos-permanentes")
                .with(como(Rol.PROFESIONAL_EXTERNO, PROFESIONAL)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.origen_inspeccion_id").value(TIGRE_NUEVA.toString()))
        .andExpect(jsonPath("$.datos.identificacion.tipologia").value("tigre-nueva"));
  }

  @Test
  void puenteSinPublicadasTraeElAnioYPermiteConfirmarSinOrigen() throws Exception {
    mvc.perform(
            get("/api/v1/puentes/" + SIN_ANIO + "/datos-permanentes")
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.origen_inspeccion_id").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.datos.identificacion.codigo").value("GT-09-0901-9205"))
        .andExpect(jsonPath("$.anio_construccion").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void confirmarGuardaPermanentesAuditaYNoTocaLaPublicada() throws Exception {
    mvc.perform(
            post("/api/v1/inspecciones/" + BORRADOR + "/confirmacion-datos-permanentes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"datos":{"identificacion":{"tipologia":"losas",\
                    "material_principal":"mamposteria","numero_tramos":3}}}
                    """)
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.datos.identificacion.material_principal").value("mamposteria"))
        .andExpect(jsonPath("$.precargada_de_id").value(PUBLICADA_RECIENTE.toString()))
        .andExpect(jsonPath("$.datos_permanentes_confirmados_en").isNotEmpty());
    assertThat(
            jdbc.sql(
                    "select datos -> 'identificacion' ->> 'material_principal' from inspeccion where id = :id")
                .param("id", PUBLICADA_RECIENTE)
                .query(String.class)
                .single())
        .isEqualTo("acero");
    assertThat(
            jdbc.sql(
                    """
                    select count(*) from auditoria
                    where entidad = 'inspeccion' and entidad_id = :id and accion = 'MODIFICAR'
                    """)
                .param("id", BORRADOR)
                .query(Long.class)
                .single())
        .isGreaterThanOrEqualTo(1);
  }

  @Test
  void confirmarUnDanoOUnTextoEnTramosEs422YNoGuarda() throws Exception {
    mvc.perform(
            post("/api/v1/inspecciones/" + BORRADOR + "/confirmacion-datos-permanentes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"datos":{"subestructura":{"estribo_entrada":{"calificacion":2}}}}
                    """)
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.errores[0].campo").value("subestructura.estribo_entrada.calificacion"));
    mvc.perform(
            post("/api/v1/inspecciones/" + BORRADOR + "/confirmacion-datos-permanentes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"datos":{"identificacion":{"numero_tramos":"tres"}}}
                    """)
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errores[0].campo").value("identificacion.numero_tramos"));
  }

  @Test
  void soloElAutorConfirmaEnBorrador() throws Exception {
    mvc.perform(
            post("/api/v1/inspecciones/" + BORRADOR + "/confirmacion-datos-permanentes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"datos":{"identificacion":{"tipologia":"losas"}}}
                    """)
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E2)))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/inspecciones/" + ENVIADA + "/confirmacion-datos-permanentes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"datos":{"identificacion":{"tipologia":"losas"}}}
                    """)
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isConflict());
    mvc.perform(
            get("/api/v1/inspecciones/" + BORRADOR + "/datos-permanentes")
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isNotFound());
  }

  @Test
  void laCorreccionUsaLaMismaUltimaPublicadaYElEstudianteSinAsignacionRecibe403() throws Exception {
    mvc.perform(
            get("/api/v1/inspecciones/" + CORRECCION + "/datos-permanentes")
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.origen_inspeccion_id").value(PUBLICADA_RECIENTE.toString()));
    mvc.perform(
            get("/api/v1/puentes/" + EL_SAUCE + "/datos-permanentes")
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/puentes/" + LAS_ROSAS + "/datos-permanentes")
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.origen_inspeccion_id").value(PUBLICADA_RECIENTE.toString()));
  }
}
