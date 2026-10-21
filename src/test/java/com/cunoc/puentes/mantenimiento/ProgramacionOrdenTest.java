package com.cunoc.puentes.mantenimiento;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.seguridad.TokenService;
import com.cunoc.puentes.usuario.Rol;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Transacciones y filtro JWT reales; no se imprimen tokens ni se usa rollback implícito de prueba.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Sql("/mantenimiento/programacion-ordenes.sql")
class ProgramacionOrdenTest extends PruebaIntegracion {
  private static final UUID PUENTE = id(1);
  private static final UUID OTRO = id(2);
  private static final UUID ADMIN = id(3);
  private static final UUID CATEDRATICO = id(4);
  private static final UUID ORDEN = id(10);
  private static final LocalDate FECHA = LocalDate.of(2026, 11, 2);
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired OrdenMantenimientoService servicio;
  @Autowired TokenService tokens;

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
    jdbc.update(
        "DELETE FROM auditoria WHERE (entidad = 'orden_mantenimiento' AND entidad_id IN (SELECT id FROM orden_mantenimiento WHERE puente_id IN (?, ?))) OR usuario_id IN (SELECT id FROM usuario WHERE correo LIKE '%@programacion-prueba.com') OR entidad_id IN (SELECT id FROM usuario WHERE correo LIKE '%@programacion-prueba.com')",
        PUENTE, OTRO);
    jdbc.update("DELETE FROM orden_mantenimiento WHERE puente_id IN (?, ?)", PUENTE, OTRO);
    jdbc.update("DELETE FROM inspeccion WHERE id = ?", id(8));
    jdbc.update("DELETE FROM formulario_version WHERE id = ?", id(7));
    jdbc.update("DELETE FROM puente WHERE id IN (?, ?)", PUENTE, OTRO);
    jdbc.update("DELETE FROM usuario WHERE id = ?", id(6));
    jdbc.update("DELETE FROM usuario WHERE correo LIKE '%@programacion-prueba.com'");
    jdbc.update("DELETE FROM municipio WHERE codigo = '9393'");
    jdbc.update("DELETE FROM departamento WHERE codigo = '93'");
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ADMINISTRADOR", "CATEDRATICO"})
  void programaConJwtRealYConservaIntervencionEstadoPrioridadesYOrigen(Rol rol) throws Exception {
    UUID usuario = rol == Rol.ADMINISTRADOR ? ADMIN : CATEDRATICO;
    String token = tokens.emitirAcceso(usuario, rol);
    mvc.perform(
            solicitud(PUENTE, ORDEN, cuerpo(FECHA))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fecha_programada").value("2026-11-02"))
        .andExpect(jsonPath("$.estado").value("PROGRAMADA"))
        .andExpect(jsonPath("$.tipo").value("CORRECTIVO"))
        .andExpect(jsonPath("$.prioridad").value("ALTA"))
        .andExpect(jsonPath("$.prioridad_sugerida").value("MEDIA"))
        .andExpect(jsonPath("$.justificacion_prioridad").value("Justificación previa"))
        .andExpect(jsonPath("$.descripcion").value("Intervención ficticia"))
        .andExpect(jsonPath("$.elemento_ref").value("elemento-de-prueba"))
        .andExpect(jsonPath("$.inspeccion_id").value(id(8).toString()))
        .andExpect(jsonPath("$.fecha_ejecucion").isEmpty());
    assertThat(
            jdbc.queryForMap(
                "SELECT usuario_id, accion, valor_anterior->>'fecha_programada' AS anterior, valor_nuevo->>'fecha_programada' AS nueva FROM auditoria WHERE entidad_id = ?",
                ORDEN))
        .containsEntry("usuario_id", usuario)
        .containsEntry("accion", "MODIFICAR")
        .containsEntry("anterior", "2026-10-25")
        .containsEntry("nueva", "2026-11-02");
  }

  @Test
  void fechaInicialPuedeEstarAusenteYLaOrdenPuedeNoTenerInspeccion() throws Exception {
    mvc.perform(solicitud(OTRO, id(16), cuerpo(FECHA)).with(como(Rol.CATEDRATICO, CATEDRATICO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fecha_programada").value(FECHA.toString()))
        .andExpect(jsonPath("$.inspeccion_id").isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"1900-01-01", "2100-12-31", "2028-02-29"})
  void noImponeRestriccionesTemporalesNoAcordadasYConservaFechaCivil(String fecha)
      throws Exception {
    mvc.perform(
            solicitud(PUENTE, ORDEN, cuerpo(LocalDate.parse(fecha)))
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fecha_programada").value(fecha));
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void otrosRolesNoProgramanPorJwtRealNiServicio(Rol rol) throws Exception {
    UUID usuario = rol == Rol.ESTUDIANTE ? id(5) : id(6);
    mvc.perform(
            solicitud(PUENTE, ORDEN, cuerpo(FECHA))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.emitirAcceso(usuario, rol)))
        .andExpect(status().isForbidden());
    autenticar(rol, usuario);
    assertThatThrownBy(() -> servicio.programarFecha(PUENTE, ORDEN, FECHA))
        .isInstanceOf(AccessDeniedException.class);
    comprobarSinCambios();
  }

  @Test
  void rolVigenteDeP2ImpideUsarUnRolPrivilegiadoAntiguoDelJwt() throws Exception {
    String token = tokens.emitirAcceso(CATEDRATICO, Rol.CATEDRATICO);
    jdbc.update("UPDATE usuario SET rol = 'ESTUDIANTE' WHERE id = ?", CATEDRATICO);
    mvc.perform(
            solicitud(PUENTE, ORDEN, cuerpo(FECHA))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden());
    comprobarSinCambios();
  }

  @Test
  void bajaRealDeCuentaP2InvalidaSuAccesoALaProgramacion() throws Exception {
    String token = tokens.emitirAcceso(CATEDRATICO, Rol.CATEDRATICO);
    mvc.perform(
            post("/api/v1/usuarios/" + CATEDRATICO + "/baja")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + tokens.emitirAcceso(ADMIN, Rol.ADMINISTRADOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Baja de prueba de integración P8\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            solicitud(PUENTE, ORDEN, cuerpo(FECHA))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnauthorized());
    assertThat(
            jdbc.queryForObject(
                "SELECT fecha_programada FROM orden_mantenimiento WHERE id = ?",
                LocalDate.class,
                ORDEN))
        .isEqualTo(LocalDate.of(2026, 10, 25));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad = 'orden_mantenimiento' AND entidad_id = ?",
                Long.class,
                ORDEN))
        .isZero();
  }

  @Test
  void sinSesionNoProgramaPorHttpNiPorServicio() throws Exception {
    mvc.perform(solicitud(PUENTE, ORDEN, cuerpo(FECHA))).andExpect(status().isUnauthorized());
    SecurityContextHolder.clearContext();
    assertThatThrownBy(() -> servicio.programarFecha(PUENTE, ORDEN, FECHA))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    comprobarSinCambios();
  }

  @ParameterizedTest
  @ValueSource(ints = {11, 12, 13, 14, 15})
  void soloProgramadaAdmiteLaFechaSinAceptarIniciarNiReabrir(int numero) throws Exception {
    mvc.perform(solicitud(PUENTE, id(numero), cuerpo(FECHA)).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isConflict());
    assertThat(auditorias()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT fecha_programada FROM orden_mantenimiento WHERE id = ?",
                LocalDate.class,
                id(numero)))
        .isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"fecha_programada\":null}",
        "{\"fecha_programada\":\"\"}",
        "{\"fecha_programada\":\"2026-02-30\"}",
        "{\"fecha_programada\":\"02/11/2026\"}",
        "{\"fecha_programada\":\"2026-11-02T12:00:00Z\"}"
      })
  void dtoInvalidoDevuelve400SinProgramar(String cuerpo) throws Exception {
    mvc.perform(solicitud(PUENTE, ORDEN, cuerpo).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isBadRequest());
    comprobarSinCambios();
  }

  @ParameterizedTest
  @CsvSource({"2,10", "1,99", "99,10"})
  void ordenAjenaOAusenteDevuelve404(int puente, int orden) throws Exception {
    mvc.perform(
            solicitud(id(puente), id(orden), cuerpo(FECHA)).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isNotFound());
    comprobarSinCambios();
  }

  @Test
  void origenInconsistenteImpideProgramar() throws Exception {
    jdbc.update("UPDATE orden_mantenimiento SET inspeccion_id = ? WHERE id = ?", id(8), id(16));
    mvc.perform(solicitud(OTRO, id(16), cuerpo(FECHA)).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isUnprocessableEntity());
    assertThat(auditorias()).isZero();
  }

  @Test
  void uuidInvalidoYNulosDelServicioNoModifican() throws Exception {
    mvc.perform(
            patch("/api/v1/puentes/no-uuid/ordenes-mantenimiento/" + ORDEN + "/fecha-programada")
                .with(como(Rol.ADMINISTRADOR, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(FECHA)))
        .andExpect(status().isBadRequest());
    autenticar(Rol.ADMINISTRADOR, ADMIN);
    assertThatThrownBy(() -> servicio.programarFecha(null, ORDEN, FECHA))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> servicio.programarFecha(PUENTE, null, FECHA))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> servicio.programarFecha(PUENTE, ORDEN, null))
        .isInstanceOf(com.cunoc.puentes.common.NegocioException.class);
    comprobarSinCambios();
  }

  @Test
  void repetirFechaNoReescribeActualizadoNiGeneraOtraAuditoria() throws Exception {
    mvc.perform(solicitud(PUENTE, ORDEN, cuerpo(FECHA)).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk());
    var antes =
        jdbc.queryForMap(
            "SELECT fecha_programada, actualizado_en FROM orden_mantenimiento WHERE id = ?", ORDEN);
    mvc.perform(solicitud(PUENTE, ORDEN, cuerpo(FECHA)).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForMap(
                "SELECT fecha_programada, actualizado_en FROM orden_mantenimiento WHERE id = ?",
                ORDEN))
        .isEqualTo(antes);
    assertThat(auditorias()).isEqualTo(1);
  }

  @Test
  void falloDeAuditoriaRevierteLaProgramacion() {
    autenticar(Rol.ADMINISTRADOR, id(99));
    assertThatThrownBy(() -> servicio.programarFecha(PUENTE, ORDEN, FECHA))
        .isInstanceOf(DataIntegrityViolationException.class);
    comprobarSinCambios();
  }

  @Test
  void dosProgramacionesIgualesConcurrentesSoloAuditanUnaVez() throws Exception {
    CountDownLatch listas = new CountDownLatch(2);
    CountDownLatch salida = new CountDownLatch(1);
    try (var ejecutor = Executors.newFixedThreadPool(2)) {
      var uno = ejecutor.submit(() -> programarConcurrente(ADMIN, listas, salida));
      var dos = ejecutor.submit(() -> programarConcurrente(CATEDRATICO, listas, salida));
      boolean preparadas = listas.await(10, TimeUnit.SECONDS);
      salida.countDown();
      assertThat(preparadas).isTrue();
      assertThat(uno.get(10, TimeUnit.SECONDS).fechaProgramada()).isEqualTo(FECHA);
      assertThat(dos.get(10, TimeUnit.SECONDS).fechaProgramada()).isEqualTo(FECHA);
    }
    assertThat(auditorias()).isEqualTo(1);
  }

  @Test
  void aceptarLuegoProgramarConservaDosDecisionesYNoConfundeProgramacionConTransicion()
      throws Exception {
    mvc.perform(
            post("/api/v1/puentes/" + PUENTE + "/ordenes-mantenimiento/" + id(11) + "/aceptacion")
                .with(como(Rol.CATEDRATICO, CATEDRATICO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fecha_programada").isEmpty());
    mvc.perform(solicitud(PUENTE, id(11), cuerpo(FECHA)).with(como(Rol.CATEDRATICO, CATEDRATICO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PROGRAMADA"))
        .andExpect(jsonPath("$.fecha_programada").value(FECHA.toString()));
    assertThat(auditorias()).isEqualTo(2);
  }

  private com.cunoc.puentes.mantenimiento.dto.OrdenMantenimientoDTO programarConcurrente(
      UUID usuario, CountDownLatch listas, CountDownLatch salida) throws InterruptedException {
    try {
      autenticar(usuario.equals(ADMIN) ? Rol.ADMINISTRADOR : Rol.CATEDRATICO, usuario);
      listas.countDown();
      if (!salida.await(10, TimeUnit.SECONDS))
        throw new IllegalStateException("No se liberó programación concurrente.");
      return servicio.programarFecha(PUENTE, ORDEN, FECHA);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private void comprobarSinCambios() {
    assertThat(
            jdbc.queryForObject(
                "SELECT fecha_programada FROM orden_mantenimiento WHERE id = ?",
                LocalDate.class,
                ORDEN))
        .isEqualTo(LocalDate.of(2026, 10, 25));
    assertThat(auditorias()).isZero();
  }

  private long auditorias() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM auditoria WHERE entidad = 'orden_mantenimiento' AND entidad_id IN (SELECT id FROM orden_mantenimiento WHERE puente_id IN (?, ?))",
        Long.class,
        PUENTE,
        OTRO);
  }

  private static void autenticar(Rol rol, UUID usuario) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                usuario.toString(),
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()))));
  }

  private static String cuerpo(LocalDate fecha) {
    return "{\"fecha_programada\":\"" + fecha + "\"}";
  }

  private static MockHttpServletRequestBuilder solicitud(UUID puente, UUID orden, String cuerpo) {
    return patch(
            "/api/v1/puentes/" + puente + "/ordenes-mantenimiento/" + orden + "/fecha-programada")
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private static UUID id(int n) {
    return UUID.fromString("019a0033-0000-7000-8000-%012d".formatted(n));
  }
}
