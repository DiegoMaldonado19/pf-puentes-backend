package com.cunoc.puentes.revision;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.indice.EstadoCondicion;
import com.cunoc.puentes.inspeccion.puerto.CalculoIndice;
import com.cunoc.puentes.usuario.Rol;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/curso/datos.sql", "/revision/datos.sql"})
@Sql(
    scripts = {"/revision/limpiar.sql", "/curso/limpiar.sql"},
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class DecisionRevisionIntegracionTest extends PruebaIntegracion {
  private static final UUID DOCENTE = UUID.fromString("0192f5a0-0000-7000-8000-000000000101");
  private static final UUID AJENO = UUID.fromString("0192f5a0-0000-7000-8000-000000000102");
  private static final UUID INSPECCION = UUID.fromString("0199b700-0000-7000-8000-000000000011");
  @Autowired RevisionService apertura;
  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @MockitoBean CalculoIndice calculo;
  private UUID revision;

  @BeforeEach
  void prepararCiclo() {
    jdbc.sql(
            "update formulario_version set esquema = cast(:esquema as jsonb) where id = '0199b700-0000-7000-8000-000000000001'")
        .param(
            "esquema",
            """
          {"type":"object","title":"Formulario original","properties":{"estribo":{"type":"object","properties":{"material":{"type":"string","title":"Material","x-elemento-ref":"estribo.material"}}}}}
          """)
        .update();
    revision = apertura.abrir(DOCENTE, INSPECCION).id();
  }

  @Test
  void anclaSoloEnVersionOriginalYConservaTextoSinExponerCalificacion() throws Exception {
    mvc.perform(
            post(base() + "/observaciones")
                .with(como(Rol.CATEDRATICO, DOCENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"elemento_ref\":\"estribo.material\",\"texto\":\"  Revisar material  \"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.texto").value("Revisar material"));
    mvc.perform(
            post(base() + "/observaciones")
                .with(como(Rol.CATEDRATICO, DOCENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"elemento_ref\":\"inventado\",\"texto\":\"Observación\"}"))
        .andExpect(status().isUnprocessableContent());
    mvc.perform(get(base()).with(como(Rol.CATEDRATICO, DOCENTE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.campos[0].referencia").value("estribo.material"))
        .andExpect(jsonPath("$.observaciones.length()").value(1))
        .andExpect(jsonPath("$.calificacion").doesNotExist());
  }

  @Test
  void cambiosExigenTextoYRegresanABorradorSinBorrarElCiclo() throws Exception {
    mvc.perform(
            post(base() + "/veredicto")
                .with(como(Rol.CATEDRATICO, DOCENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"veredicto\":\"CAMBIOS_SOLICITADOS\",\"observacion\":\"   \"}"))
        .andExpect(status().isUnprocessableContent());
    decidir("CAMBIOS_SOLICITADOS", "Corregir material")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("BORRADOR"));
    assertThat(
            jdbc.sql("select veredicto from revision where id = :id")
                .param("id", revision)
                .query(String.class)
                .single())
        .isEqualTo("CAMBIOS_SOLICITADOS");
    assertThat(
            jdbc.sql("select count(*) from transicion_inspeccion where inspeccion_id = :id")
                .param("id", INSPECCION)
                .query(Long.class)
                .single())
        .isEqualTo(3);
    decidir("RECHAZADA", "Otro intento").andExpect(status().isConflict());
  }

  @Test
  void rechazoEsTerminalYExigeObservacion() throws Exception {
    mvc.perform(
            post(base() + "/veredicto")
                .with(como(Rol.CATEDRATICO, DOCENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"veredicto\":\"RECHAZADA\"}"))
        .andExpect(status().isUnprocessableContent());
    decidir("RECHAZADA", "Evidencia incompatible")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("RECHAZADA"));
    mvc.perform(
            post(base() + "/observaciones")
                .with(como(Rol.CATEDRATICO, DOCENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"elemento_ref\":\"estribo.material\",\"texto\":\"Tarde\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void aprobacionPublicaConCalculoYNoPermiteSegundoVeredicto() throws Exception {
    when(calculo.calcular(any(), any()))
        .thenReturn(
            new CalculoIndice.ResultadoCalculo(
                new BigDecimal("85"), EstadoCondicion.BUENO, null, null));
    decidir("APROBADA", "Conforme")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PUBLICADA"));
    assertThat(
            jdbc.sql("select indice_condicion from inspeccion where id = :id")
                .param("id", INSPECCION)
                .query(BigDecimal.class)
                .single())
        .isEqualByComparingTo("85");
    decidir("APROBADA", "Repetido").andExpect(status().isConflict());
    assertThat(
            jdbc.sql("select count(*) from hilo where inspeccion_id = :id")
                .param("id", INSPECCION)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void falloDelCalculoRevierteVeredictoYPublicacion() throws Exception {
    when(calculo.calcular(any(), any()))
        .thenThrow(
            new com.cunoc.puentes.common.NegocioException(
                org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT,
                "sin-pesos",
                "Sin pesos",
                "Falta configuración"));
    decidir("APROBADA", "Conforme").andExpect(status().isUnprocessableContent());
    assertThat(
            jdbc.sql(
                    "select count(*) from revision where id = :id and veredicto is null and emitida_en is null")
                .param("id", revision)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql("select estado from inspeccion where id = :id")
                .param("id", INSPECCION)
                .query(String.class)
                .single())
        .isEqualTo("EN_REVISION");
  }

  @Test
  void aprobarTambienExigeObservacion() throws Exception {
    decidir("APROBADA", "").andExpect(status().isUnprocessableContent());
    decidir("APROBADA", "   ")
        .andExpect(status().isUnprocessableContent())
        .andExpect(
            jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("observacion-requerida")));
    mvc.perform(
            post(base() + "/veredicto")
                .with(como(Rol.CATEDRATICO, DOCENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"veredicto\":\"APROBADA\"}"))
        .andExpect(status().isUnprocessableContent());
    assertThat(
            jdbc.sql("select veredicto from revision where id = :id")
                .param("id", revision)
                .query(String.class)
                .optional())
        .isEmpty();
  }

  @Test
  void autorizaCadaOperacionPorRolYRevisor() throws Exception {
    mvc.perform(get(base())).andExpect(status().isUnauthorized());
    mvc.perform(get(base()).with(como(Rol.ESTUDIANTE, AJENO))).andExpect(status().isForbidden());
    mvc.perform(get(base()).with(como(Rol.CATEDRATICO, AJENO))).andExpect(status().isNotFound());
    mvc.perform(
            post(base() + "/veredicto")
                .with(como(Rol.CATEDRATICO, AJENO))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"veredicto\":\"APROBADA\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(get(base() + "/observaciones").with(como(Rol.CATEDRATICO, AJENO)))
        .andExpect(status().isNotFound());
  }

  private org.springframework.test.web.servlet.ResultActions decidir(
      String veredicto, String observacion) throws Exception {
    return mvc.perform(
        post(base() + "/veredicto")
            .with(como(Rol.CATEDRATICO, DOCENTE))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"veredicto\":\"" + veredicto + "\",\"observacion\":\"" + observacion + "\"}"));
  }

  private String base() {
    return "/api/v1/revisiones/" + revision;
  }
}
