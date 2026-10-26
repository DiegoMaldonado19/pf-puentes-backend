package com.cunoc.puentes.indice;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.usuario.Rol;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql({"/indice/servicio-pesos.sql", "/indice/creacion-juegos.sql"})
class RecalculoIcTest extends PruebaIntegracion {

  private static final UUID FORM = UUID.fromString("019a0018-0000-7000-8000-000000000001");
  private static final UUID OTRA_FORM = UUID.fromString("019a0018-0000-7000-8000-000000000002");
  private static final UUID ADMIN = UUID.fromString("019a0018-0000-7000-8000-000000000003");
  private static final UUID CATEDRATICO = UUID.fromString("019a0018-0000-7000-8000-000000000004");
  private static final UUID PUENTE = UUID.fromString("019a0040-0000-7000-8000-000000000001");
  private static final UUID JUEGO_ORIGINAL =
      UUID.fromString("019a0040-0000-7000-8000-000000000011");
  private static final UUID JUEGO_NUEVO = UUID.fromString("019a0040-0000-7000-8000-000000000015");
  private static final UUID INSPECCION = UUID.fromString("019a0040-0000-7000-8000-000000000021");
  private static final String RUTA = "/api/v1/inspecciones/{inspeccionId}/recalculos-ic";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entidades;
  @Autowired RecalculoIcService servicio;

  @BeforeEach
  void prepararDatosCompletos() {
    jdbc.update(
        """
        INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol, estado, correo_verificado_en)
        VALUES (?, 'catedratico-p8@prueba.invalid', 'Catedrático P8', 'hash', 'CATEDRATICO', 'ACTIVO', now())
        """,
        CATEDRATICO);
    jdbc.update(
        "INSERT INTO peso_elemento(id, version_pesos_id, elemento_ref, peso) VALUES (gen_random_uuid(), ?, 'elemento.b', 3)",
        JUEGO_ORIGINAL);
    jdbc.update(
        "INSERT INTO version_pesos(id, formulario_version_id, acta) VALUES (?, ?, 'Acta calibrada v2')",
        JUEGO_NUEVO,
        FORM);
    jdbc.update(
        """
        INSERT INTO peso_elemento(id, version_pesos_id, elemento_ref, peso) VALUES
          (gen_random_uuid(), ?, 'elemento.a', 1),
          (gen_random_uuid(), ?, 'elemento.b', 2)
        """,
        JUEGO_NUEVO,
        JUEGO_NUEVO);
    jdbc.update(
        """
        UPDATE puente
        SET estado_actual = 'BUENO',
            indice_condicion_actual = 80.00,
            fecha_ultima_inspeccion = '2026-10-01'
        WHERE id = ?
        """,
        PUENTE);
    jdbc.update(
        "UPDATE inspeccion SET datos = '{\"a\": 0, \"b\": 2}'::jsonb WHERE id = ?", INSPECCION);
  }

  @AfterEach
  void limpiarContexto() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void recalculaConJuegoNuevoConservandoOriginalPuenteYAuditandoUnaSolaVezEnReintentos()
      throws Exception {
    var inspeccionAntes = jdbc.queryForMap("SELECT * FROM inspeccion WHERE id = ?", INSPECCION);
    var puenteAntes = jdbc.queryForMap("SELECT * FROM puente WHERE id = ?", PUENTE);

    // Con a=0 (peso 1) y b=2 (peso 2): suma daños = 4, daño máx = 4*(1+2)=12 -> IC = 66.666... ->
    // 66.67 REGULAR
    String respuesta =
        mvc.perform(
                post(RUTA, INSPECCION)
                    .with(como(Rol.CATEDRATICO, CATEDRATICO))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version_pesos_id\":\"" + JUEGO_NUEVO + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inspeccion_id").value(INSPECCION.toString()))
            .andExpect(jsonPath("$.version_pesos_id").value(JUEGO_NUEVO.toString()))
            .andExpect(jsonPath("$.formulario_version_id").value(FORM.toString()))
            .andExpect(jsonPath("$.acta").value("Acta calibrada v2"))
            .andExpect(jsonPath("$.indice_condicion").value(66.67))
            .andExpect(jsonPath("$.estado_calculado").value("REGULAR"))
            .andExpect(jsonPath("$.anulacion").value((Object) null))
            .andExpect(jsonPath("$.creado_en").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    entidades.flush();
    assertThat(jdbc.queryForMap("SELECT * FROM inspeccion WHERE id = ?", INSPECCION))
        .isEqualTo(inspeccionAntes);
    assertThat(jdbc.queryForMap("SELECT * FROM puente WHERE id = ?", PUENTE))
        .isEqualTo(puenteAntes);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM recalculo_ic WHERE inspeccion_id = ?",
                Long.class,
                INSPECCION))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad = 'recalculo_ic' AND accion = 'CREAR'",
                Long.class))
        .isEqualTo(1L);

    // Reintento idempotente con el mismo juego: no duplica fila ni auditoría
    String reintento =
        mvc.perform(
                post(RUTA, INSPECCION)
                    .with(como(Rol.ADMINISTRADOR, ADMIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version_pesos_id\":\"" + JUEGO_NUEVO + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    entidades.flush();
    assertThat(reintento).isEqualTo(respuesta);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM recalculo_ic WHERE inspeccion_id = ?",
                Long.class,
                INSPECCION))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad = 'recalculo_ic' AND accion = 'CREAR'",
                Long.class))
        .isEqualTo(1L);
  }

  @Test
  void consultaComparacionDistinguiendoResultadoOriginalYRecalculosFechados() throws Exception {
    mvc.perform(
            post(RUTA, INSPECCION)
                .with(como(Rol.ADMINISTRADOR, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version_pesos_id\":\"" + JUEGO_NUEVO + "\"}"))
        .andExpect(status().isOk());

    mvc.perform(get(RUTA, INSPECCION).with(como(Rol.CATEDRATICO, CATEDRATICO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.original.inspeccion_id").value(INSPECCION.toString()))
        .andExpect(jsonPath("$.original.puente_id").value(PUENTE.toString()))
        .andExpect(jsonPath("$.original.formulario_version_id").value(FORM.toString()))
        .andExpect(jsonPath("$.original.version_pesos_id").value(JUEGO_ORIGINAL.toString()))
        .andExpect(jsonPath("$.original.acta").value("Acta anterior"))
        .andExpect(jsonPath("$.original.indice_condicion").value(80.0))
        .andExpect(jsonPath("$.original.estado_calculado").value("BUENO"))
        .andExpect(jsonPath("$.original.estado_confirmado").value("BUENO"))
        .andExpect(jsonPath("$.original.publicada_en").isNotEmpty())
        .andExpect(jsonPath("$.recalculos.total_elements").value(1))
        .andExpect(
            jsonPath("$.recalculos.content[0].version_pesos_id").value(JUEGO_NUEVO.toString()))
        .andExpect(jsonPath("$.recalculos.content[0].acta").value("Acta calibrada v2"))
        .andExpect(jsonPath("$.recalculos.content[0].indice_condicion").value(66.67))
        .andExpect(jsonPath("$.recalculos.content[0].estado_calculado").value("REGULAR"));
  }

  @ParameterizedTest
  @EnumSource(Anulacion.class)
  void conservaCadaAnulacionCriticaYForzaMaloAunqueElPromedioSeaAlto(Anulacion causa) {
    jdbc.update(
        "UPDATE inspeccion SET datos = '{\"a\": 0, \"b\": 0}'::jsonb, anulacion = ? WHERE id = ?",
        causa.name(),
        INSPECCION);
    autenticar(Rol.ADMINISTRADOR);

    var recalculo = servicio.recalcular(INSPECCION, JUEGO_NUEVO);
    assertThat(recalculo.indiceCondicion()).isEqualByComparingTo(new BigDecimal("100.00"));
    assertThat(recalculo.estadoCalculado()).isEqualTo(EstadoCondicion.MALO);
    assertThat(recalculo.anulacion()).isEqualTo(causa);

    var comparacion = servicio.consultar(INSPECCION, 0, 20);
    assertThat(comparacion.original().anulacion()).isEqualTo(causa);
    assertThat(comparacion.recalculos().content()).hasSize(1);
    assertThat(comparacion.recalculos().content().getFirst().anulacion()).isEqualTo(causa);
  }

  @ParameterizedTest
  @ValueSource(strings = {"BORRADOR", "EN_REVISION", "RECHAZADA"})
  void rechazaInspeccionesNoPublicadasEnConsultaYRecalculo(String estadoNoPublicado)
      throws Exception {
    UUID borrador = UUID.fromString("019a0040-0000-7000-8000-000000000029");
    jdbc.update(
        """
        INSERT INTO inspeccion(
          id, puente_id, autor_id, formulario_version_id, estado, fecha_inspeccion, datos,
          ubicacion_inicio, dispositivo_id
        ) VALUES (?, ?, ?, ?, ?, '2026-10-02', '{"a": 1, "b": 1}'::jsonb,
          'SRID=4326;POINT(-90.5 14.6)', 'disp-borrador')
        """,
        borrador,
        PUENTE,
        ADMIN,
        FORM,
        estadoNoPublicado);

    mvc.perform(get(RUTA, borrador).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type")
                .value("https://sgp.cunoc.usac.edu.gt/errores/inspeccion-no-publicada"));

    mvc.perform(
            post(RUTA, borrador)
                .with(como(Rol.ADMINISTRADOR, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version_pesos_id\":\"" + JUEGO_NUEVO + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type")
                .value("https://sgp.cunoc.usac.edu.gt/errores/inspeccion-no-publicada"));
  }

  @Test
  void rechazaRecursosInexistentesJuegosIncompatiblesYDatosInvalidos() throws Exception {
    UUID ausente = UUID.fromString("019a0040-0000-7000-8000-000000000099");

    mvc.perform(get(RUTA, ausente).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type")
                .value("https://sgp.cunoc.usac.edu.gt/errores/inspeccion-no-encontrada"));

    mvc.perform(
            post(RUTA, ausente)
                .with(como(Rol.ADMINISTRADOR, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version_pesos_id\":\"" + JUEGO_NUEVO + "\"}"))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type")
                .value("https://sgp.cunoc.usac.edu.gt/errores/inspeccion-no-encontrada"));

    mvc.perform(
            post(RUTA, INSPECCION)
                .with(como(Rol.ADMINISTRADOR, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version_pesos_id\":\"" + ausente + "\"}"))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type")
                .value("https://sgp.cunoc.usac.edu.gt/errores/juego-pesos-no-encontrado"));

    UUID juegoOtraVersion = UUID.fromString("019a0040-0000-7000-8000-000000000018");
    jdbc.update(
        "INSERT INTO version_pesos(id, formulario_version_id) VALUES (?, ?)",
        juegoOtraVersion,
        OTRA_FORM);

    mvc.perform(
            post(RUTA, INSPECCION)
                .with(como(Rol.ADMINISTRADOR, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version_pesos_id\":\"" + juegoOtraVersion + "\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(
            jsonPath("$.type")
                .value("https://sgp.cunoc.usac.edu.gt/errores/juego-pesos-de-otra-version"));

    jdbc.update("UPDATE inspeccion SET datos = '[]'::jsonb WHERE id = ?", INSPECCION);
    mvc.perform(
            post(RUTA, INSPECCION)
                .with(como(Rol.ADMINISTRADOR, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version_pesos_id\":\"" + JUEGO_NUEVO + "\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(
            jsonPath("$.type")
                .value("https://sgp.cunoc.usac.edu.gt/errores/datos-inspeccion-invalidos"));
  }

  @Test
  void validaParametrosNulosYPaginacionEnServicioYControlador() throws Exception {
    autenticar(Rol.ADMINISTRADOR);
    assertThatThrownBy(() -> servicio.consultar(null, 0, 20))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> servicio.recalcular(null, JUEGO_NUEVO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> servicio.recalcular(INSPECCION, null))
        .isInstanceOf(IllegalArgumentException.class);

    assertPaginacionInvalida(-1, 20);
    assertPaginacionInvalida(0, 0);
    assertPaginacionInvalida(0, 101);
    assertPaginacionInvalida(Integer.MAX_VALUE, 2);

    mvc.perform(get(RUTA, INSPECCION).param("page", "-1").with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isBadRequest());

    mvc.perform(
            post(RUTA, INSPECCION)
                .with(como(Rol.ADMINISTRADOR, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void restringeAccesoACatedraticoYAdministradorEnHttpYServicio(Rol rolSinPermiso)
      throws Exception {
    mvc.perform(get(RUTA, INSPECCION)).andExpect(status().isUnauthorized());
    mvc.perform(
            post(RUTA, INSPECCION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version_pesos_id\":\"" + JUEGO_NUEVO + "\"}"))
        .andExpect(status().isUnauthorized());

    mvc.perform(get(RUTA, INSPECCION).with(como(rolSinPermiso))).andExpect(status().isForbidden());
    mvc.perform(
            post(RUTA, INSPECCION)
                .with(como(rolSinPermiso))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version_pesos_id\":\"" + JUEGO_NUEVO + "\"}"))
        .andExpect(status().isForbidden());

    SecurityContextHolder.clearContext();
    assertThatThrownBy(() -> servicio.consultar(INSPECCION, 0, 20))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

    autenticar(rolSinPermiso);
    assertThatThrownBy(() -> servicio.consultar(INSPECCION, 0, 20))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> servicio.recalcular(INSPECCION, JUEGO_NUEVO))
        .isInstanceOf(AccessDeniedException.class);
  }

  private void assertPaginacionInvalida(int pagina, int tamano) {
    assertThatThrownBy(() -> servicio.consultar(INSPECCION, pagina, tamano))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            error -> {
              NegocioException negocio = (NegocioException) error;
              assertThat(negocio.getStatusCode().value()).isEqualTo(400);
              assertThat(negocio.getBody().getType().toString())
                  .hasToString(
                      "https://sgp.cunoc.usac.edu.gt/errores/pagina-de-recalculos-invalida");
            });
  }

  private static void autenticar(Rol rol) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                ADMIN, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()))));
  }
}
