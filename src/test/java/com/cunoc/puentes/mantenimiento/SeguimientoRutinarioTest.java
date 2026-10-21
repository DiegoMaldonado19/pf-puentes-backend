package com.cunoc.puentes.mantenimiento;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.seguridad.TokenService;
import com.cunoc.puentes.usuario.Rol;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Sql("/mantenimiento/seguimiento-rutinario.sql")
class SeguimientoRutinarioTest extends PruebaIntegracion {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired TokenService tokens;
  @Autowired SeguimientoRutinarioService servicio;
  @Autowired SeguimientoRutinarioRepository seguimiento;

  @MockitoBean(name = "relojMantenimiento")
  Clock reloj;

  private static final UUID PUENTE = id(1);
  private static final UUID ADMIN = id(3);

  @BeforeEach
  void reloj() {
    when(reloj.instant()).thenReturn(Instant.parse("2026-02-28T12:00:00Z"));
    when(reloj.getZone()).thenReturn(ZoneOffset.UTC);
  }

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
    jdbc.update(
        "DELETE FROM orden_mantenimiento WHERE puente_id IN (SELECT id FROM puente WHERE municipio_codigo = '9191')");
    jdbc.update("DELETE FROM puente WHERE municipio_codigo = '9191'");
    jdbc.update("DELETE FROM usuario WHERE id = ?", id(6));
    jdbc.update("DELETE FROM usuario WHERE correo LIKE '%@anual-prueba.com'");
    jdbc.update("DELETE FROM municipio WHERE codigo = '9191'");
    jdbc.update("DELETE FROM departamento WHERE codigo = '91'");
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ADMINISTRADOR", "CATEDRATICO"})
  void consultaConJwtRealResumeTodasLasAbiertasSinConfundirProgramacionConEjecucion(Rol rol)
      throws Exception {
    UUID usuario = rol == Rol.ADMINISTRADOR ? ADMIN : id(4);
    mvc.perform(
            get(ruta(PUENTE))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.emitirAcceso(usuario, rol)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.puente_id").value(PUENTE.toString()))
        .andExpect(jsonPath("$.fecha_evaluacion").value("2026-02-28"))
        .andExpect(jsonPath("$.fecha_referencia").value("2025-03-01"))
        .andExpect(jsonPath("$.origen_referencia").value("EJECUCION_RUTINARIA"))
        .andExpect(jsonPath("$.fecha_limite").value("2026-03-01"))
        .andExpect(jsonPath("$.programadas_en_plazo").value(2))
        .andExpect(jsonPath("$.sin_fecha").value(1))
        .andExpect(jsonPath("$.fechas_pasadas").value(1))
        .andExpect(jsonPath("$.posteriores_al_limite").value(1))
        .andExpect(jsonPath("$.ordenes.total_elements").value(5))
        .andExpect(jsonPath("$.ordenes.content[0].id").value(id(17).toString()));
  }

  @Test
  void paginaNoModificaConteosGlobalesYRespetaOrdenYTipo() throws Exception {
    mvc.perform(get(ruta(PUENTE) + "?page=1&size=2").with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.programadas_en_plazo").value(2))
        .andExpect(jsonPath("$.ordenes.page").value(1))
        .andExpect(jsonPath("$.ordenes.total_pages").value(3))
        .andExpect(jsonPath("$.ordenes.content.length()").value(2))
        .andExpect(jsonPath("$.ordenes.content[0].id").value(id(15).toString()));
    mvc.perform(get(ruta(PUENTE) + "?page=100&size=2").with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ordenes.content.length()").value(0))
        .andExpect(jsonPath("$.ordenes.total_elements").value(5));
  }

  @Test
  void sinEjecucionUsaRegistroCivilUtcYNoOtroPuente() throws Exception {
    mvc.perform(get(ruta(id(2))).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.origen_referencia").value("REGISTRO_PUENTE"))
        .andExpect(jsonPath("$.fecha_referencia").value("2024-03-01"))
        .andExpect(jsonPath("$.fecha_limite").value("2025-03-01"))
        .andExpect(jsonPath("$.programadas_en_plazo").value(0))
        .andExpect(jsonPath("$.ordenes.total_elements").value(0));
  }

  @Test
  void anioBisiestoYLimitePasadoNoSeReemplazanPor365Dias() throws Exception {
    jdbc.update(
        "UPDATE orden_mantenimiento SET fecha_ejecucion = '2024-02-29' WHERE id = ?", id(10));
    jdbc.update(
        "UPDATE orden_mantenimiento SET fecha_ejecucion = '2024-01-01' WHERE id = ?", id(11));
    mvc.perform(get(ruta(PUENTE)).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fecha_limite").value("2025-02-28"))
        .andExpect(jsonPath("$.programadas_en_plazo").value(0))
        .andExpect(jsonPath("$.fechas_pasadas").value(1))
        .andExpect(jsonPath("$.posteriores_al_limite").value(4));
  }

  @Test
  void hoyIgualAlLimiteCuentaYAlDiaSiguienteYaNo() throws Exception {
    when(reloj.instant()).thenReturn(Instant.parse("2026-03-01T12:00:00Z"));
    mvc.perform(get(ruta(PUENTE)).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.programadas_en_plazo").value(1));
    when(reloj.instant()).thenReturn(Instant.parse("2026-03-02T12:00:00Z"));
    mvc.perform(get(ruta(PUENTE)).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.programadas_en_plazo").value(0));
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void otrosRolesNoConsultanPorJwtNiServicio(Rol rol) throws Exception {
    UUID usuario = rol == Rol.ESTUDIANTE ? id(5) : id(6);
    mvc.perform(
            get(ruta(PUENTE))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.emitirAcceso(usuario, rol)))
        .andExpect(status().isForbidden());
    autenticar(rol, usuario);
    assertThatThrownBy(() -> servicio.consultar(PUENTE, 0, 20))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void sinSesionOPerdidaDeRolNoLee() throws Exception {
    mvc.perform(get(ruta(PUENTE))).andExpect(status().isUnauthorized());
    assertThatThrownBy(() -> servicio.consultar(PUENTE, 0, 20))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    String token = tokens.emitirAcceso(id(4), Rol.CATEDRATICO);
    jdbc.update("UPDATE usuario SET rol = 'ESTUDIANTE' WHERE id = ?", id(4));
    mvc.perform(get(ruta(PUENTE)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"?page=-1", "?size=0", "?size=101", "?page=2147483647&size=100", "?page=texto"})
  void paginacionInvalida400(String parametros) throws Exception {
    mvc.perform(get(ruta(PUENTE) + parametros).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void puenteAusente404YUuidInvalido400() throws Exception {
    mvc.perform(get(ruta(id(99))).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/puentes/no-uuid/ordenes-mantenimiento/rutinario-anual")
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isBadRequest());
    autenticar(Rol.ADMINISTRADOR, ADMIN);
    assertThatThrownBy(() -> servicio.consultar(null, 0, 20))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> servicio.consultar(PUENTE, -1, 20))
        .isInstanceOf(com.cunoc.puentes.common.NegocioException.class);
  }

  @Test
  void consultaNoModificaOrdenesNiGeneraAuditoriaORecordatorios() throws Exception {
    var antes =
        jdbc.queryForList(
            "SELECT * FROM orden_mantenimiento WHERE puente_id = ? ORDER BY id", PUENTE);
    mvc.perform(get(ruta(PUENTE)).with(como(Rol.ADMINISTRADOR, ADMIN))).andExpect(status().isOk());
    assertThat(
            jdbc.queryForList(
                "SELECT * FROM orden_mantenimiento WHERE puente_id = ? ORDER BY id", PUENTE))
        .isEqualTo(antes);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE usuario_id IN (?,?)",
                Long.class,
                ADMIN,
                id(4)))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notificacion WHERE usuario_id IN (?,?)",
                Long.class,
                ADMIN,
                id(4)))
        .isZero();
  }

  @Test
  void deteccionPorLotesCoincideConElResumenYConservaLosPuentesSinProgramacion() {
    LocalDate hoy = LocalDate.of(2026, 2, 28);
    assertThat(seguimiento.sinProgramacion(null, hoy)).contains(id(2)).doesNotContain(PUENTE);
    autenticar(Rol.ADMINISTRADOR, ADMIN);
    assertThat(servicio.consultar(PUENTE, 0, 20).programadasEnPlazo()).isEqualTo(2);
    assertThat(servicio.consultar(id(2), 0, 20).programadasEnPlazo()).isZero();
    jdbc.update(
        "UPDATE orden_mantenimiento SET fecha_programada = NULL WHERE id IN (?,?)", id(13), id(14));
    assertThat(seguimiento.sinProgramacion(null, hoy)).contains(PUENTE, id(2));
    assertThat(servicio.consultar(PUENTE, 0, 20).programadasEnPlazo()).isZero();
  }

  @Test
  void deteccionNoConfundePropuestasFechasPasadasOFechasPosterioresConProgramacionValida() {
    jdbc.update(
        "UPDATE orden_mantenimiento SET fecha_programada = '2026-03-02' WHERE id = ?", id(13));
    jdbc.update(
        "UPDATE orden_mantenimiento SET fecha_programada = '2026-02-27' WHERE id = ?", id(14));
    assertThat(seguimiento.sinProgramacion(null, LocalDate.of(2026, 2, 28))).contains(PUENTE);
    jdbc.update(
        "UPDATE orden_mantenimiento SET fecha_programada = '2026-03-01' WHERE id = ?", id(14));
    assertThat(seguimiento.sinProgramacion(null, LocalDate.of(2026, 2, 28))).doesNotContain(PUENTE);
  }

  @Test
  void deteccionRespetaLimiteInclusivoYElDiaPosterior() {
    assertThat(seguimiento.sinProgramacion(null, LocalDate.of(2026, 3, 1))).doesNotContain(PUENTE);
    assertThat(seguimiento.sinProgramacion(null, LocalDate.of(2026, 3, 2))).contains(PUENTE);
  }

  @Test
  void deteccionUsaElMismoAnioBisiestoQueLaConsulta() {
    jdbc.update(
        "UPDATE orden_mantenimiento SET fecha_ejecucion = '2024-02-29' WHERE id = ?", id(10));
    jdbc.update(
        "UPDATE orden_mantenimiento SET fecha_ejecucion = '2024-01-01' WHERE id = ?", id(11));
    jdbc.update(
        "UPDATE orden_mantenimiento SET fecha_programada = '2025-02-28' WHERE id = ?", id(13));
    jdbc.update("UPDATE orden_mantenimiento SET fecha_programada = NULL WHERE id = ?", id(14));
    assertThat(seguimiento.sinProgramacion(null, LocalDate.of(2025, 2, 28))).doesNotContain(PUENTE);
    assertThat(seguimiento.sinProgramacion(null, LocalDate.of(2025, 3, 1))).contains(PUENTE);
  }

  @Test
  void deteccionRecorreMasDeCienPuentesSinDuplicadosNiOmisiones() {
    jdbc.update(
        """
        INSERT INTO puente (id,codigo,nombre,municipio_codigo,ruta,ubicacion,creado_en)
        SELECT ('019a0035-0000-7000-8000-' || lpad(n::text,12,'0'))::uuid,
          'GT-91-9191-' || n, 'Puente ficticio', '9191', 'Ruta ficticia',
          'SRID=4326;POINT(-90.51 14.62)', '2026-01-01T00:00:00Z'
        FROM generate_series(1000,1100) n
        """);
    LocalDate hoy = LocalDate.of(2026, 2, 28);
    var primera = seguimiento.sinProgramacion(null, hoy);
    var segunda = seguimiento.sinProgramacion(primera.getLast(), hoy);
    assertThat(primera).hasSize(100).doesNotHaveDuplicates();
    assertThat(segunda).hasSize(2).doesNotHaveDuplicates().doesNotContainAnyElementsOf(primera);
    assertThat(seguimiento.sinProgramacion(segunda.getLast(), hoy)).isEmpty();
    assertThat(primera).contains(id(2)).doesNotContain(PUENTE);
  }

  private static void autenticar(Rol rol, UUID usuario) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                usuario.toString(),
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()))));
  }

  private static String ruta(UUID puente) {
    return "/api/v1/puentes/" + puente + "/ordenes-mantenimiento/rutinario-anual";
  }

  private static UUID id(int n) {
    return UUID.fromString("019a0035-0000-7000-8000-%012d".formatted(n));
  }
}
