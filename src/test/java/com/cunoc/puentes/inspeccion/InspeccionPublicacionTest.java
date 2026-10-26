package com.cunoc.puentes.inspeccion;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.indice.EstadoCondicion;
import com.cunoc.puentes.usuario.Rol;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/inspeccion/consulta.sql", "/inspeccion/borrador.sql"})
@Sql(
    scripts = "/inspeccion/consulta-limpiar.sql",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class InspeccionPublicacionTest extends PruebaIntegracion {

  static final UUID E1 = InspeccionConsultaTest.E1;
  static final UUID C1 = InspeccionConsultaTest.C1;
  static final UUID ADMIN = InspeccionConsultaTest.ADMIN;
  static final UUID PROFESIONAL = InspeccionConsultaTest.PROFESIONAL;
  static final UUID PENDIENTE = InspeccionConsultaTest.id(0x17);
  static final UUID EL_SAUCE = InspeccionConsultaTest.id(0x3);
  static final UUID FORMULARIO = InspeccionConsultaTest.id(0x21);
  static final UUID PESOS = InspeccionConsultaTest.id(0x61);
  static final UUID BORRADOR = InspeccionConsultaTest.BORRADOR_E1;
  static final UUID BORRADOR_PRO = InspeccionConsultaTest.BORRADOR_PRO;
  static final UUID BORRADOR_CAT = InspeccionConsultaTest.id(0xf1);
  static final UUID CORRECCION = InspeccionConsultaTest.id(0xf2);
  static final UUID PUBLICADA_2024 = InspeccionConsultaTest.id(0xf3);
  static final UUID ENVIADA = InspeccionConsultaTest.ENVIADA;
  static final UUID RECHAZADA = InspeccionConsultaTest.RECHAZADA;
  static final UUID PUBLICADA = InspeccionConsultaTest.PUBLICADA_RECIENTE;
  static final String BASE = "/api/v1/inspecciones/";

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired PublicacionInspeccionService publicacion;

  @BeforeEach
  @AfterEach
  void reiniciarOyentes() {
    OyentesPublicacionDePrueba.reiniciar();
    SecurityContextHolder.clearContext();
  }

  @Test
  void catedraticoEnviaYPublicaConHistorialCompletoEIgnoraElIcDelCliente() throws Exception {
    insertarBorrador(BORRADOR_CAT, EL_SAUCE, C1, LocalDate.of(2026, 10, 20), null, null);
    preparar(BORRADOR_CAT, InspeccionEnvioTest.DATOS_COMPLETOS, 4);
    mvc.perform(
            post(BASE + BORRADOR_CAT + "/envio")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enviada_en":"2026-10-25T16:05:00Z","indice_condicion":1.00,\
                    "estado_calculado":"MALO"}
                    """)
                .with(csrf())
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PUBLICADA"))
        .andExpect(jsonPath("$.indice_condicion").value(65.0))
        .andExpect(jsonPath("$.estado_calculado").value("REGULAR"))
        .andExpect(jsonPath("$.estado_confirmado").value("REGULAR"))
        .andExpect(jsonPath("$.publicada_en").isNotEmpty());
    mvc.perform(get(BASE + BORRADOR_CAT + "/historial").with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].estado_origen").value("BORRADOR"))
        .andExpect(jsonPath("$[0].estado_destino").value("ENVIADA"))
        .andExpect(jsonPath("$[0].actor_id").value(C1.toString()))
        .andExpect(jsonPath("$[1].estado_origen").value("ENVIADA"))
        .andExpect(jsonPath("$[1].estado_destino").value("PUBLICADA"))
        .andExpect(jsonPath("$[1].actor_id").value(C1.toString()));
    assertThat(
            jdbc.sql(
                    """
                    select valor_anterior->>'estado', valor_nuevo->>'estado'
                    from auditoria
                    where entidad = 'inspeccion' and entidad_id = :id and accion = 'CAMBIAR_ESTADO'
                    """)
                .param("id", BORRADOR_CAT)
                .query((rs, n) -> rs.getString(1) + "->" + rs.getString(2))
                .list())
        .contains("BORRADOR->ENVIADA", "ENVIADA->PUBLICADA");
    assertThat(OyentesPublicacionDePrueba.propuestas).containsExactly(BORRADOR_CAT);
    assertThat(OyentesPublicacionDePrueba.hilos).containsExactly(BORRADOR_CAT);
    assertThat(puente("estado_actual")).isEqualTo("REGULAR");
    assertThat(new BigDecimal(puente("indice_condicion_actual"))).isEqualByComparingTo("65.00");
    assertThat(puente("fecha_ultima_inspeccion")).isEqualTo("2026-10-20");
  }

  @Test
  void profesionalPendienteNoPublica() throws Exception {
    jdbc.sql(
            """
            insert into usuario (id, correo, nombre, contrasena_hash, rol, estado, numero_colegiado)
            values (:id, 'pro-pend-p5@cunoc.edu.gt', 'Pendiente', 'hash',
              'PROFESIONAL_EXTERNO', 'PENDIENTE', '15999')
            """)
        .param("id", PENDIENTE)
        .update();
    UUID borrador = InspeccionConsultaTest.id(0xf4);
    insertarBorrador(borrador, EL_SAUCE, PENDIENTE, LocalDate.of(2026, 10, 20), null, null);
    preparar(borrador, InspeccionEnvioTest.DATOS_COMPLETOS, 4);
    mvc.perform(enviar(borrador).with(como(Rol.PROFESIONAL_EXTERNO, PENDIENTE)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(Matchers.endsWith("publicacion-directa-no-permitida")));
    assertThat(estado(borrador)).isEqualTo("BORRADOR");
    assertThat(OyentesPublicacionDePrueba.propuestas).isEmpty();
  }

  @Test
  void aprobarLaInspeccionDeUnEstudiantePublicaConElRevisor() throws Exception {
    preparar(BORRADOR, InspeccionEnvioTest.DATOS_COMPLETOS, 4);
    mvc.perform(enviar(BORRADOR).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ENVIADA"));
    UUID revision = ciclo(BORRADOR, C1);
    autenticar(C1, Rol.CATEDRATICO);
    publicacion.abrirRevision(BORRADOR, C1, revision);
    publicacion.emitirVeredicto(
        BORRADOR, C1, revision, VeredictoRevision.APROBADA, null, null, null);
    assertThat(estado(BORRADOR)).isEqualTo("PUBLICADA");
    assertThat(
            jdbc.sql(
                    """
                    select estado_origen, estado_destino, actor_id, revision_id
                    from transicion_inspeccion
                    where inspeccion_id = :id and estado_destino = 'PUBLICADA'
                    """)
                .param("id", BORRADOR)
                .query(
                    (rs, n) ->
                        rs.getString(1)
                            + "->"
                            + rs.getString(2)
                            + ":"
                            + rs.getObject(3, UUID.class)
                            + ":"
                            + rs.getObject(4, UUID.class))
                .single())
        .isEqualTo("EN_REVISION->PUBLICADA:" + C1 + ":" + revision);
    assertThat(OyentesPublicacionDePrueba.propuestas).contains(BORRADOR);
  }

  @Test
  void revisorAjustaElEstadoConJustificacionYExigeResponsable() throws Exception {
    preparar(BORRADOR, InspeccionEnvioTest.DATOS_COMPLETOS, 4);
    mvc.perform(enviar(BORRADOR).with(como(Rol.ESTUDIANTE, E1))).andExpect(status().isOk());
    UUID revision = ciclo(BORRADOR, C1);
    autenticar(C1, Rol.CATEDRATICO);
    publicacion.abrirRevision(BORRADOR, C1, revision);
    assertThatThrownBy(
            () ->
                publicacion.emitirVeredicto(
                    BORRADOR,
                    C1,
                    revision,
                    VeredictoRevision.APROBADA,
                    null,
                    EstadoCondicion.MALO,
                    null))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            error -> {
              NegocioException negocio = (NegocioException) error;
              assertThat(negocio.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(negocio.getBody().getType().toString())
                  .endsWith("justificacion-estado-requerida");
            });
    assertThat(estado(BORRADOR)).isEqualTo("EN_REVISION");
    publicacion.emitirVeredicto(
        BORRADOR,
        C1,
        revision,
        VeredictoRevision.APROBADA,
        null,
        EstadoCondicion.MALO,
        "Grieta en la viga principal");
    assertThat(estado(BORRADOR)).isEqualTo("PUBLICADA");
    assertThat(
            jdbc.sql(
                    """
                    select estado_calculado, estado_confirmado, justificacion_estado,
                      estado_confirmado_por
                    from inspeccion where id = :id
                    """)
                .param("id", BORRADOR)
                .query(
                    (rs, n) ->
                        rs.getString(1)
                            + "|"
                            + rs.getString(2)
                            + "|"
                            + rs.getString(3)
                            + "|"
                            + rs.getObject(4, UUID.class))
                .single())
        .isEqualTo("REGULAR|MALO|Grieta en la viga principal|" + C1);
  }

  @ParameterizedTest
  @CsvSource({
    "0,0,0,0,2,0,0,90.00,BUENO,",
    "2,2,2,1,1,0,0,60.00,REGULAR,",
    "4,2,2,2,2,0,0,40.00,MALO,",
    "0,0,0,0,0,1,0,100.00,MALO,SOCAVACION",
    "0,0,0,0,1,0,1,95.00,MALO,ASENTAMIENTO"
  })
  void elEstadoSaleDelIcYDeLasAnulaciones(
      int e1,
      int e2,
      int losa,
      int acceso,
      int cauce,
      int socavacion,
      int asentamiento,
      String ic,
      String estado,
      String anulacion)
      throws Exception {
    preparar(BORRADOR_PRO, datos(e1, e2, losa, acceso, cauce, socavacion, asentamiento), 4);
    mvc.perform(enviar(BORRADOR_PRO).with(como(Rol.PROFESIONAL_EXTERNO, PROFESIONAL)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PUBLICADA"))
        .andExpect(jsonPath("$.indice_condicion").value(Double.parseDouble(ic)))
        .andExpect(jsonPath("$.estado_calculado").value(estado))
        .andExpect(jsonPath("$.estado_confirmado").value(estado));
    String causa =
        jdbc.sql("select anulacion from inspeccion where id = :id")
            .param("id", BORRADOR_PRO)
            .query(String.class)
            .optional()
            .orElse(null);
    if (anulacion == null || anulacion.isBlank()) {
      assertThat(causa).isNull();
    } else {
      assertThat(causa).isEqualTo(anulacion);
    }
    assertThat(
            jdbc.sql("select version_pesos_id from inspeccion where id = :id")
                .param("id", BORRADOR_PRO)
                .query(UUID.class)
                .single())
        .isEqualTo(PESOS);
  }

  @Test
  void sinIcCalculableNoSePublicaNiSeEmiteEvento() {
    jdbc.sql("update inspeccion set datos = '{}'::jsonb where id = :id")
        .param("id", ENVIADA)
        .update();
    autenticar(PROFESIONAL, Rol.PROFESIONAL_EXTERNO);
    assertThatThrownBy(() -> publicacion.publicarDirecta(ENVIADA, PROFESIONAL))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            error -> {
              NegocioException negocio = (NegocioException) error;
              assertThat(negocio.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(negocio.getBody().getType().toString()).endsWith("ic-no-calculable");
            });
    assertThat(estado(ENVIADA)).isEqualTo("ENVIADA");
    assertThat(OyentesPublicacionDePrueba.propuestas).isEmpty();
  }

  @Test
  void publicarBorradorRechazadaOPublicadaEs409() {
    autenticar(C1, Rol.CATEDRATICO);
    for (UUID id : List.of(BORRADOR, RECHAZADA, PUBLICADA)) {
      String previo = estado(id);
      assertThatThrownBy(() -> publicacion.publicarDirecta(id, C1))
          .isInstanceOf(NegocioException.class)
          .satisfies(
              error -> {
                NegocioException negocio = (NegocioException) error;
                assertThat(negocio.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(negocio.getBody().getType().toString()).endsWith("transicion-invalida");
              });
      assertThat(estado(id)).isEqualTo(previo);
    }
    assertThat(OyentesPublicacionDePrueba.propuestas).isEmpty();
  }

  @Test
  void siElOyenteFallaNoSePublicaNada() throws Exception {
    insertarBorrador(BORRADOR_CAT, EL_SAUCE, C1, LocalDate.of(2026, 10, 20), null, null);
    preparar(BORRADOR_CAT, InspeccionEnvioTest.DATOS_COMPLETOS, 4);
    OyentesPublicacionDePrueba.fallarMantenimiento.set(true);
    mvc.perform(enviar(BORRADOR_CAT).with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado."))
        .andExpect(
            jsonPath("$.detail").value(Matchers.not(Matchers.containsString("mantenimiento"))));
    assertThat(estado(BORRADOR_CAT)).isEqualTo("BORRADOR");
    assertThat(puente("estado_actual")).isNull();
    assertThat(
            jdbc.sql(
                    """
                    select count(*) from transicion_inspeccion
                    where inspeccion_id = :id and estado_destino = 'PUBLICADA'
                    """)
                .param("id", BORRADOR_CAT)
                .query(Long.class)
                .single())
        .isZero();
    assertThat(
            jdbc.sql("select indice_condicion from inspeccion where id = :id")
                .param("id", BORRADOR_CAT)
                .query(BigDecimal.class)
                .optional())
        .isEmpty();
  }

  @Test
  void publicadaNoSeEditaNiSeEliminaNiSeRepublica() throws Exception {
    preparar(BORRADOR_PRO, InspeccionEnvioTest.DATOS_COMPLETOS, 4);
    UUID foto =
        jdbc.sql("select id from archivo where inspeccion_id = :id limit 1")
            .param("id", BORRADOR_PRO)
            .query(UUID.class)
            .single();
    mvc.perform(enviar(BORRADOR_PRO).with(como(Rol.PROFESIONAL_EXTERNO, PROFESIONAL)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PUBLICADA"));
    mvc.perform(
            put(BASE + BORRADOR_PRO)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"datos\":{\"identificacion\":{\"tipologia\":\"x\"}},\"version\":1}")
                .with(csrf())
                .with(como(Rol.PROFESIONAL_EXTERNO, PROFESIONAL)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(Matchers.endsWith("inspeccion-no-editable")));
    mvc.perform(
            put(BASE + BORRADOR_PRO + "/acompanantes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acompanantes\":[{\"nombre\":\"Nadie\"}]}")
                .with(csrf())
                .with(como(Rol.PROFESIONAL_EXTERNO, PROFESIONAL)))
        .andExpect(status().isConflict());
    mvc.perform(
            delete(BASE + BORRADOR_PRO)
                .with(csrf())
                .with(como(Rol.PROFESIONAL_EXTERNO, PROFESIONAL)))
        .andExpect(status().isConflict());
    mvc.perform(
            delete("/api/v1/archivos/" + foto)
                .with(csrf())
                .with(como(Rol.PROFESIONAL_EXTERNO, PROFESIONAL)))
        .andExpect(status().isConflict());
    mvc.perform(enviar(BORRADOR_PRO).with(como(Rol.PROFESIONAL_EXTERNO, PROFESIONAL)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(Matchers.endsWith("inspeccion-no-editable")));
    autenticar(PROFESIONAL, Rol.PROFESIONAL_EXTERNO);
    assertThatThrownBy(() -> publicacion.publicarDirecta(BORRADOR_PRO, PROFESIONAL))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            error ->
                assertThat(((NegocioException) error).getBody().getType().toString())
                    .endsWith("transicion-invalida"));
    assertThat(OyentesPublicacionDePrueba.propuestas).containsExactly(BORRADOR_PRO);
  }

  @Test
  void unRecalculoDePesosNoAlteraLaPublicada() throws Exception {
    preparar(BORRADOR_PRO, InspeccionEnvioTest.DATOS_COMPLETOS, 4);
    mvc.perform(enviar(BORRADOR_PRO).with(como(Rol.PROFESIONAL_EXTERNO, PROFESIONAL)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.indice_condicion").value(65.0));
    UUID otra = InspeccionConsultaTest.id(0x62);
    jdbc.sql(
            """
            insert into version_pesos (id, formulario_version_id, vigente_desde)
            values (:id, :formulario, now())
            """)
        .param("id", otra)
        .param("formulario", FORMULARIO)
        .update();
    jdbc.sql(
            """
            insert into peso_elemento (id, version_pesos_id, elemento_ref, peso)
            values (:id, :version, 'cauce', 9.000)
            """)
        .param("id", InspeccionConsultaTest.id(0x76))
        .param("version", otra)
        .update();
    mvc.perform(get(BASE + BORRADOR_PRO).with(como(Rol.PROFESIONAL_EXTERNO, PROFESIONAL)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.indice_condicion").value(65.0))
        .andExpect(jsonPath("$.estado_calculado").value("REGULAR"));
    assertThat(
            jdbc.sql("select version_pesos_id from inspeccion where id = :id")
                .param("id", BORRADOR_PRO)
                .query(UUID.class)
                .single())
        .isEqualTo(PESOS);
  }

  @Test
  void publicarUnaCorreccionMarcaALaOriginalComoSuperada() throws Exception {
    insertarBorrador(BORRADOR_CAT, EL_SAUCE, C1, LocalDate.of(2026, 10, 20), null, null);
    preparar(BORRADOR_CAT, datos(0, 0, 0, 0, 2, 0, 0), 4);
    mvc.perform(enviar(BORRADOR_CAT).with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PUBLICADA"));
    insertarBorrador(
        CORRECCION, EL_SAUCE, C1, LocalDate.of(2026, 10, 21), BORRADOR_CAT, "Error de cauce");
    preparar(CORRECCION, datos(0, 0, 0, 0, 0, 0, 0), 4);
    mvc.perform(enviar(CORRECCION).with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PUBLICADA"))
        .andExpect(jsonPath("$.supersede_a_id").value(BORRADOR_CAT.toString()));
    mvc.perform(get(BASE + BORRADOR_CAT).with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.superada").value(true))
        .andExpect(jsonPath("$.superada_por").value(CORRECCION.toString()));
    assertThat(OyentesPublicacionDePrueba.propuestas).contains(BORRADOR_CAT, CORRECCION);
  }

  @Test
  void corregirUnaPublicadaAntiguaNoHaceRetrocederAlPuente() throws Exception {
    insertarPublicada(PUBLICADA_2024, EL_SAUCE, C1, LocalDate.of(2024, 6, 1), "40.00", "MALO");
    insertarBorrador(BORRADOR_CAT, EL_SAUCE, C1, LocalDate.of(2026, 10, 20), null, null);
    preparar(BORRADOR_CAT, datos(0, 0, 0, 0, 2, 0, 0), 4);
    mvc.perform(enviar(BORRADOR_CAT).with(como(Rol.CATEDRATICO, C1))).andExpect(status().isOk());
    assertThat(puente("fecha_ultima_inspeccion")).isEqualTo("2026-10-20");
    assertThat(new BigDecimal(puente("indice_condicion_actual"))).isEqualByComparingTo("90.00");
    insertarBorrador(
        CORRECCION, EL_SAUCE, C1, LocalDate.of(2024, 6, 15), PUBLICADA_2024, "Cauce mal medido");
    preparar(CORRECCION, datos(0, 0, 0, 0, 0, 0, 0), 4);
    mvc.perform(enviar(CORRECCION).with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PUBLICADA"));
    assertThat(puente("fecha_ultima_inspeccion")).isEqualTo("2026-10-20");
    assertThat(new BigDecimal(puente("indice_condicion_actual"))).isEqualByComparingTo("90.00");
    assertThat(puente("estado_actual")).isEqualTo("BUENO");
  }

  @Test
  void estudianteAdminOVisitanteNoPublicanDirecto() throws Exception {
    preparar(BORRADOR, InspeccionEnvioTest.DATOS_COMPLETOS, 4);
    mvc.perform(enviar(BORRADOR).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ENVIADA"));
    autenticar(E1, Rol.ESTUDIANTE);
    assertThatThrownBy(() -> publicacion.publicarDirecta(BORRADOR, E1))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            error ->
                assertThat(((NegocioException) error).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN));
    assertThat(estado(BORRADOR)).isEqualTo("ENVIADA");
    mvc.perform(enviar(BORRADOR_PRO).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isForbidden());
    mvc.perform(enviar(BORRADOR_PRO).with(csrf())).andExpect(status().isUnauthorized());
    assertThat(estado(BORRADOR_PRO)).isEqualTo("BORRADOR");
  }

  private void insertarBorrador(
      UUID id, UUID puente, UUID autor, LocalDate fecha, UUID original, String motivo) {
    jdbc.sql(
            """
            insert into inspeccion
              (id, puente_id, autor_id, formulario_version_id, estado, fecha_inspeccion,
               supersede_a_id, motivo_correccion)
            values (:id, :puente, :autor, :formulario, 'BORRADOR', :fecha, :original, :motivo)
            """)
        .param("id", id)
        .param("puente", puente)
        .param("autor", autor)
        .param("formulario", FORMULARIO)
        .param("fecha", fecha)
        .param("original", original)
        .param("motivo", motivo)
        .update();
  }

  private void insertarPublicada(
      UUID id, UUID puente, UUID autor, LocalDate fecha, String ic, String estado) {
    jdbc.sql(
            """
            insert into inspeccion
              (id, puente_id, autor_id, formulario_version_id, estado, fecha_inspeccion,
               indice_condicion, estado_calculado, estado_confirmado, publicada_en, enviada_en)
            values (:id, :puente, :autor, :formulario, 'PUBLICADA', :fecha,
              CAST(:ic AS numeric), :estado, :estado, now(), now())
            """)
        .param("id", id)
        .param("puente", puente)
        .param("autor", autor)
        .param("formulario", FORMULARIO)
        .param("fecha", fecha)
        .param("ic", ic)
        .param("estado", estado)
        .update();
  }

  private void preparar(UUID inspeccion, String datos, int vigentes) {
    jdbc.sql(
            """
            update inspeccion
            set datos = CAST(:datos AS jsonb),
                datos_permanentes_confirmados_en = :conf,
                fecha_inspeccion = fecha_inspeccion
            where id = :id
            """)
        .param("datos", datos)
        .param("conf", Timestamp.from(Instant.parse("2026-10-20T12:00:00Z")))
        .param("id", inspeccion)
        .update();
    jdbc.sql("delete from archivo where inspeccion_id = :id").param("id", inspeccion).update();
    for (int i = 0; i < vigentes; i++) {
      UUID foto = UUID.randomUUID();
      jdbc.sql(
              """
              insert into archivo (id, inspeccion_id, tipo, clave, clave_miniatura, tamano)
              values (:id, :ins, 'WEBP', :clave, :mini, 10)
              """)
          .param("id", foto)
          .param("ins", inspeccion)
          .param("clave", "pub/" + inspeccion + "/" + foto + ".webp")
          .param("mini", "pub/" + inspeccion + "/" + foto + "-mini.webp")
          .update();
    }
  }

  private UUID ciclo(UUID inspeccion, UUID revisor) {
    UUID revision = InspeccionConsultaTest.id(0xf8);
    jdbc.sql("insert into revision (id, inspeccion_id, revisor_id) values (:id, :ins, :rev)")
        .param("id", revision)
        .param("ins", inspeccion)
        .param("rev", revisor)
        .update();
    return revision;
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder enviar(
      UUID id) {
    return post(BASE + id + "/envio")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}")
        .with(csrf());
  }

  private String estado(UUID id) {
    return jdbc.sql("select estado from inspeccion where id = :id")
        .param("id", id)
        .query(String.class)
        .single();
  }

  private String puente(String columna) {
    return jdbc.sql("select " + columna + " from puente where id = :id")
        .param("id", EL_SAUCE)
        .query(String.class)
        .optional()
        .orElse(null);
  }

  private static String datos(
      int e1, int e2, int losa, int acceso, int cauce, int socavacion, int asentamiento) {
    return """
        {"identificacion":{"tipologia":"losas","material_principal":"concreto",\
        "numero_tramos":2,"longitud_m":18.0,"ancho_calzada_m":6.0,"anio_construccion":1998},\
        "subestructura":{"estribo_entrada":{"calificacion":%d,"fisuras":0,"humedad":0,\
        "desconchado":0,"asentamiento":%d,"socavacion":%d},"estribo_salida":{"calificacion":%d}},\
        "superestructura":{"losa":{"calificacion":%d}},\
        "accesos":{"entrada":{"calificacion":%d}},"cauce":{"calificacion":%d}}
        """
        .formatted(e1, asentamiento, socavacion, e2, losa, acceso, cauce);
  }

  private static void autenticar(UUID usuario, Rol rol) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                usuario.toString(),
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()))));
  }
}
