package com.cunoc.puentes.inspeccion;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.inspeccion.dto.CrearInspeccionDTO;
import com.cunoc.puentes.usuario.Rol;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Sql("/inspeccion/consulta.sql")
@Sql(
    scripts = "/inspeccion/consulta-limpiar.sql",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class InspeccionInicioTest extends PruebaIntegracion {

  static final UUID LAS_ROSAS = InspeccionConsultaTest.LAS_ROSAS;
  static final UUID EL_SAUCE = InspeccionConsultaTest.id(0x3);
  static final UUID BAJA = InspeccionConsultaTest.id(0x4);
  static final UUID SIN_ANIO = InspeccionConsultaTest.id(0x5);
  static final UUID E1 = InspeccionConsultaTest.E1;
  static final UUID C1 = InspeccionConsultaTest.C1;
  static final UUID PROFESIONAL = InspeccionConsultaTest.PROFESIONAL;
  static final UUID ADMIN = InspeccionConsultaTest.ADMIN;
  static final UUID ASIGNACION_ROSAS = InspeccionConsultaTest.id(0x51);
  static final String BASE = "/api/v1/inspecciones";

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired InicioInspeccionService inicio;
  @Autowired EscrituraAltaInspeccion altas;
  @Autowired jakarta.persistence.EntityManager em;
  @Autowired com.cunoc.puentes.common.seguridad.TokenService tokens;

  @Test
  void profesionalRequiereCuentaActivaYColegiadoVerificadoTambienConJwtAnterior() throws Exception {
    String bearer = "Bearer " + tokens.emitirAcceso(PROFESIONAL, Rol.PROFESIONAL_EXTERNO);
    mvc.perform(get("/api/v1/puentes-inspeccionables").header("Authorization", bearer))
        .andExpect(status().isOk());
    mvc.perform(
            crear(InspeccionConsultaTest.id(0xbc1), LAS_ROSAS, hoy(), null, null)
                .header("Authorization", bearer))
        .andExpect(status().isCreated());

    jdbc.sql(
            """
        update usuario set estado = 'PENDIENTE',
          colegiado_verificado_en = null, colegiado_verificado_por = null
        where id = :id
        """)
        .param("id", PROFESIONAL)
        .update();
    mvc.perform(get("/api/v1/puentes-inspeccionables").header("Authorization", bearer))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            crear(InspeccionConsultaTest.id(0xbc2), LAS_ROSAS, hoy(), null, null)
                .header("Authorization", bearer))
        .andExpect(status().isUnauthorized());
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.sql("update usuario set estado = 'ACTIVO' where id = :id")
                    .param("id", PROFESIONAL)
                    .update())
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  @org.springframework.transaction.annotation.Transactional
  void altaAuditadaConPuentesAppNoNecesitaUpdateEnBitacora() {
    jdbc.sql(
            "do $$ begin if not exists (select from pg_roles where rolname = 'puentes_app') then create role puentes_app; end if; end $$")
        .update();
    jdbc.sql("grant usage on schema public to puentes_app").update();
    jdbc.sql("grant select on all tables in schema public to puentes_app").update();
    jdbc.sql("grant insert on inspeccion, transicion_inspeccion, auditoria to puentes_app")
        .update();
    jdbc.sql("grant update on inspeccion to puentes_app").update();
    jdbc.sql("revoke update, delete on auditoria from puentes_app").update();
    jdbc.sql("set local role puentes_app").update();
    Inspeccion nueva = new Inspeccion();
    UUID id = UUID.randomUUID();
    nueva.setId(id);
    nueva.setPuenteId(LAS_ROSAS);
    nueva.setAutorId(E1);
    nueva.setFormularioVersionId(
        jdbc.sql("select id from formulario_version where estado = 'ACTIVA'")
            .query(UUID.class)
            .single());
    nueva.setFechaInspeccion(hoy());
    nueva.setDispositivoId("tablet-restringida");
    nueva.setDatos(java.util.Map.of("identificacion", java.util.Map.of("numero_tramos", 1L)));
    autenticar(E1, Rol.ESTUDIANTE);
    try {
      altas.persistirParticipando(nueva);
      em.flush();
      // Una segunda consulta obliga a revisar también el snapshot JSON numérico de la auditoría.
      assertThat(
              jdbc.sql("select count(*) from auditoria where entidad_id = :id")
                  .param("id", id)
                  .query(Long.class)
                  .single())
          .isEqualTo(1);
      assertThat(
              jdbc.sql("select has_table_privilege(current_user, 'auditoria', 'UPDATE')")
                  .query(Boolean.class)
                  .single())
          .isFalse();
    } finally {
      jdbc.sql("reset role").update();
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void estudianteVeSoloPuentesDeCursosVigentesYMarcaBorradorEnCurso() throws Exception {
    mvc.perform(get("/api/v1/puentes-inspeccionables").with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(
            jsonPath("$.content[*].nombre")
                .value(org.hamcrest.Matchers.hasItems("Las Rosas", "El Tigre")))
        .andExpect(
            jsonPath("$.content[*].nombre")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("El Sauce"))))
        .andExpect(
            jsonPath("$.content[?(@.nombre == 'Las Rosas')].borrador_en_curso")
                .value(org.hamcrest.Matchers.hasItem(true)))
        .andExpect(
            jsonPath("$.content[?(@.nombre == 'Las Rosas')].curso_nombre")
                .value(org.hamcrest.Matchers.hasItem("Puentes 2026")));
  }

  @ParameterizedTest
  @CsvSource({"21, PROFESIONAL_EXTERNO", "19, CATEDRATICO"})
  void profesionalYCatedraticoVenActivosPaginados(int actor, Rol rol) throws Exception {
    mvc.perform(
            get("/api/v1/puentes-inspeccionables")
                .param("size", "100")
                .with(como(rol, InspeccionConsultaTest.id(actor))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(org.hamcrest.Matchers.lessThanOrEqualTo(100)))
        .andExpect(
            jsonPath("$.content[*].nombre")
                .value(org.hamcrest.Matchers.hasItems("Las Rosas", "El Tigre")))
        .andExpect(
            jsonPath("$.content[*].nombre")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Baja P5"))));
  }

  @Test
  void administradorYVisitanteNoInspeccionan() throws Exception {
    mvc.perform(get("/api/v1/puentes-inspeccionables").with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/puentes-inspeccionables")).andExpect(status().isUnauthorized());
  }

  @Test
  void estudianteIniciaBorradorConHistorialYAuditoria() throws Exception {
    UUID inspeccion = InspeccionConsultaTest.id(0xaa1);
    mvc.perform(crear(inspeccion, LAS_ROSAS, hoy(), null, null).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(inspeccion.toString()))
        .andExpect(jsonPath("$.autor_id").value(E1.toString()))
        .andExpect(jsonPath("$.estado").value("BORRADOR"))
        .andExpect(jsonPath("$.formulario.codigo").value("SIECA-1"))
        .andExpect(jsonPath("$.curso_id").value(InspeccionConsultaTest.id(0x31).toString()))
        .andExpect(jsonPath("$.datos").exists())
        .andExpect(jsonPath("$.creado_en").exists());
    assertThat(
            jdbc.sql(
                    "select count(*) from transicion_inspeccion where inspeccion_id = :id and estado_origen is null and estado_destino = 'BORRADOR'")
                .param("id", inspeccion)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql(
                    "select count(*) from auditoria where accion = 'CREAR' and entidad = 'inspeccion' and entidad_id = :id")
                .param("id", inspeccion)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource({
    "17, ESTUDIANTE, 1, 201",
    "17, ESTUDIANTE, 3, 403",
    "21, PROFESIONAL_EXTERNO, 1, 201",
    "19, CATEDRATICO, 1, 201",
    "22, ADMINISTRADOR, 1, 403"
  })
  void quienPuedeIniciar(int actor, Rol rol, int puente, int respuesta) throws Exception {
    UUID inspeccion = InspeccionConsultaTest.id(0xab0 + actor * 10 + puente);
    mvc.perform(
            crear(inspeccion, InspeccionConsultaTest.id(puente), hoy(), null, null)
                .with(como(rol, InspeccionConsultaTest.id(actor))))
        .andExpect(status().is(respuesta));
  }

  @Test
  void catedraticoNoLigaCursoYPuenteInactivoEs422() throws Exception {
    UUID inspeccion = InspeccionConsultaTest.id(0xac1);
    mvc.perform(crear(inspeccion, LAS_ROSAS, hoy(), null, null).with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.curso_id").doesNotExist());
    mvc.perform(
            crear(InspeccionConsultaTest.id(0xac2), BAJA, hoy(), null, null)
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("puente-inactivo")));
  }

  @Test
  void validaFechaEnGuatemalaYAnioDeConstruccion() throws Exception {
    LocalDate hoy = hoy();
    mvc.perform(
            crear(InspeccionConsultaTest.id(0xad1), LAS_ROSAS, hoy, null, null)
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isCreated());
    mvc.perform(
            crear(InspeccionConsultaTest.id(0xad2), LAS_ROSAS, hoy.plusDays(1), null, null)
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errores[0].campo").value("fecha_inspeccion"));
    mvc.perform(
            crear(InspeccionConsultaTest.id(0xad3), LAS_ROSAS, LocalDate.of(1997, 6, 1), null, null)
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errores[0].campo").value("fecha_inspeccion"));
    mvc.perform(
            crear(InspeccionConsultaTest.id(0xad4), LAS_ROSAS, LocalDate.of(1998, 1, 1), null, null)
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isCreated());
    mvc.perform(
            crear(InspeccionConsultaTest.id(0xad5), SIN_ANIO, LocalDate.of(1990, 1, 1), null, null)
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isCreated());
  }

  @Test
  void sinFormularioActivoEs409YLaVersionQuedaCongelada() throws Exception {
    jdbc.sql("update formulario_version set estado = 'RETIRADA' where estado = 'ACTIVA'").update();
    mvc.perform(
            crear(InspeccionConsultaTest.id(0xae1), LAS_ROSAS, hoy(), null, null)
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("sin-formulario-activo")));
    jdbc.sql("update formulario_version set estado = 'ACTIVA' where id = :id")
        .param("id", InspeccionConsultaTest.id(0x21))
        .update();
    UUID creada = InspeccionConsultaTest.id(0xae2);
    mvc.perform(crear(creada, LAS_ROSAS, hoy(), null, null).with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.formulario.codigo").value("SIECA-1"));
    jdbc.sql("update formulario_version set estado = 'RETIRADA' where id = :id")
        .param("id", InspeccionConsultaTest.id(0x21))
        .update();
    jdbc.sql(
            """
            insert into formulario_version (id, codigo, esquema, estado, publicada_en)
            values (:id, 'SIECA-2', '{}'::jsonb, 'ACTIVA', now())
            """)
        .param("id", InspeccionConsultaTest.id(0x22))
        .update();
    mvc.perform(get(BASE + "/" + creada).with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.formulario.codigo").value("SIECA-1"));
  }

  @Test
  void gpsCercanoLejanoYAusente() throws Exception {
    Punto cercano = proyectar(LAS_ROSAS, 120);
    Punto lejano = proyectar(LAS_ROSAS, 612);
    mvc.perform(
            crear(InspeccionConsultaTest.id(0xaf1), LAS_ROSAS, hoy(), cercano.lat, cercano.lon)
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.advertencias").isEmpty())
        .andExpect(
            jsonPath("$.distancia_a_puente_m").value(org.hamcrest.Matchers.closeTo(120.0, 15.0)));
    mvc.perform(
            crear(InspeccionConsultaTest.id(0xaf2), LAS_ROSAS, hoy(), lejano.lat, lejano.lon)
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isCreated())
        .andExpect(
            jsonPath("$.advertencias[0]")
                .value(org.hamcrest.Matchers.containsString("m del puente")))
        .andExpect(
            jsonPath("$.distancia_a_puente_m").value(org.hamcrest.Matchers.closeTo(612.0, 15.0)));
    mvc.perform(
            crear(InspeccionConsultaTest.id(0xaf3), LAS_ROSAS, hoy(), null, null)
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.gps_disponible").value(false))
        .andExpect(jsonPath("$.advertencia_ubicacion").value("GPS no disponible"));
    mvc.perform(
            post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    cuerpo(
                        InspeccionConsultaTest.id(0xaf4),
                        LAS_ROSAS,
                        hoy(),
                        120.0,
                        -91.518,
                        null,
                        null))
                .with(csrf())
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores[0].campo").value("latitud"));
  }

  @Test
  void reenvioIdempotenteYConflictoSiElIdEsAjeno() throws Exception {
    UUID inspeccion = InspeccionConsultaTest.id(0xb01);
    var peticion = crear(inspeccion, LAS_ROSAS, hoy(), null, null);
    mvc.perform(peticion.with(como(Rol.CATEDRATICO, C1))).andExpect(status().isCreated());
    mvc.perform(peticion.with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(inspeccion.toString()));
    assertThat(
            jdbc.sql("select count(*) from transicion_inspeccion where inspeccion_id = :id")
                .param("id", inspeccion)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql("select count(*) from auditoria where accion = 'CREAR' and entidad_id = :id")
                .param("id", inspeccion)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    mvc.perform(
            crear(inspeccion, LAS_ROSAS, hoy(), null, null)
                .with(como(Rol.PROFESIONAL_EXTERNO, PROFESIONAL)))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("C1"))));
  }

  @Test
  void creacionConcurrenteConElMismoIdNoDuplica() throws Exception {
    UUID inspeccion = InspeccionConsultaTest.id(0xb02);
    CrearInspeccionDTO datos =
        new CrearInspeccionDTO(
            inspeccion, LAS_ROSAS, null, null, hoy(), "tablet", null, null, null, null, null);
    CountDownLatch listos = new CountDownLatch(1);
    Callable<Integer> operacion =
        () -> {
          autenticar(C1, Rol.CATEDRATICO);
          listos.await();
          try {
            inicio.iniciar(C1, Rol.CATEDRATICO, datos);
            return 201;
          } catch (NegocioException e) {
            return e.getStatusCode().value();
          } finally {
            SecurityContextHolder.clearContext();
          }
        };
    try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      var una = ejecutor.submit(operacion);
      var otra = ejecutor.submit(operacion);
      listos.countDown();
      assertThat(List.of(una.get(15, TimeUnit.SECONDS), otra.get(15, TimeUnit.SECONDS)))
          .containsOnly(201);
    }
    assertThat(
            jdbc.sql("select count(*) from inspeccion where id = :id")
                .param("id", inspeccion)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql("select count(*) from transicion_inspeccion where inspeccion_id = :id")
                .param("id", inspeccion)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void correccionDePublicadaYReglasDeOrigen() throws Exception {
    mvc.perform(
            crear(
                    InspeccionConsultaTest.id(0xb10),
                    LAS_ROSAS,
                    hoy(),
                    null,
                    null,
                    InspeccionConsultaTest.PUBLICADA_RECIENTE,
                    "Error en la profundidad del estribo")
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("correccion-en-curso")));
    mvc.perform(
            post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    cuerpo(
                        InspeccionConsultaTest.id(0xb11),
                        LAS_ROSAS,
                        hoy(),
                        null,
                        null,
                        InspeccionConsultaTest.PUBLICADA_PRO,
                        "   "))
                .with(csrf())
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores[0].campo").value("motivo_correccion"));
    mvc.perform(
            crear(
                    InspeccionConsultaTest.id(0xb12),
                    LAS_ROSAS,
                    hoy(),
                    null,
                    null,
                    InspeccionConsultaTest.BORRADOR_E1,
                    "motivo")
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("inspeccion-no-corregible")));
    mvc.perform(
            crear(
                    InspeccionConsultaTest.id(0xb13),
                    LAS_ROSAS,
                    hoy(),
                    null,
                    null,
                    InspeccionConsultaTest.RECHAZADA,
                    "motivo")
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            crear(
                    InspeccionConsultaTest.id(0xb14),
                    EL_SAUCE,
                    hoy(),
                    null,
                    null,
                    InspeccionConsultaTest.PUBLICADA_PRO,
                    "motivo")
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            crear(
                    InspeccionConsultaTest.id(0xb15),
                    LAS_ROSAS,
                    hoy(),
                    null,
                    null,
                    InspeccionConsultaTest.id(0x99),
                    "motivo")
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isNotFound());
    mvc.perform(
            crear(
                    InspeccionConsultaTest.id(0xb16),
                    LAS_ROSAS,
                    hoy(),
                    null,
                    null,
                    InspeccionConsultaTest.PUBLICADA_PRO,
                    "Error en la profundidad del estribo")
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isCreated())
        .andExpect(
            jsonPath("$.supersede_a_id").value(InspeccionConsultaTest.PUBLICADA_PRO.toString()))
        .andExpect(jsonPath("$.datos.identificacion.codigo").value("GT-09-0901-9201"))
        .andExpect(jsonPath("$.datos.subestructura").doesNotExist());
    mvc.perform(
            crear(
                    InspeccionConsultaTest.id(0xb17),
                    EL_SAUCE,
                    hoy(),
                    null,
                    null,
                    InspeccionConsultaTest.PUBLICADA_PRO,
                    "motivo")
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isForbidden());
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder crear(
      UUID id, UUID puente, LocalDate fecha, Double lat, Double lon) {
    return crear(id, puente, fecha, lat, lon, null, null);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder crear(
      UUID id, UUID puente, LocalDate fecha, Double lat, Double lon, UUID original, String motivo) {
    return post(BASE)
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(id, puente, fecha, lat, lon, original, motivo))
        .with(csrf());
  }

  private static String cuerpo(
      UUID id, UUID puente, LocalDate fecha, Double lat, Double lon, UUID original, String motivo) {
    StringBuilder json = new StringBuilder();
    json.append("{\"id\":\"")
        .append(id)
        .append("\",\"puente_id\":\"")
        .append(puente)
        .append("\",\"asignacion_id\":\"")
        .append(ASIGNACION_ROSAS)
        .append("\",\"fecha_inspeccion\":\"")
        .append(fecha)
        .append("\",\"dispositivo_id\":\"tablet-01\"");
    if (lat != null) {
      json.append(",\"latitud\":").append(lat);
    }
    if (lon != null) {
      json.append(",\"longitud\":").append(lon);
    }
    if (original != null) {
      json.append(",\"supersede_a_id\":\"").append(original).append("\"");
    }
    if (motivo != null) {
      json.append(",\"motivo_correccion\":\"").append(motivo).append("\"");
    }
    return json.append("}").toString();
  }

  private Punto proyectar(UUID puenteId, int metros) {
    return jdbc.sql(
            """
            select round(st_y(geom::geometry)::numeric, 6) as lat,
                   round(st_x(geom::geometry)::numeric, 6) as lon
            from (select st_project(ubicacion, :metros, 0) as geom from puente where id = :id) t
            """)
        .param("metros", metros)
        .param("id", puenteId)
        .query(Punto.class)
        .single();
  }

  private static void autenticar(UUID usuario, Rol rol) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                usuario.toString(),
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()))));
  }

  private static LocalDate hoy() {
    return LocalDate.now(ValidacionFechaInspeccion.ZONA);
  }

  private record Punto(double lat, double lon) {}
}
