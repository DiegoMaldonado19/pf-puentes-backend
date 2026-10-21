package com.cunoc.puentes.mantenimiento;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.Rol;
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
import org.springframework.dao.DataIntegrityViolationException;
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

/** Comprueba commits, no-op, rollback y concurrencia reales; no usa transacción de prueba. */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/mantenimiento/ajuste-prioridad-ordenes.sql")
class AjustePrioridadOrdenTest extends PruebaIntegracion {
  private static final UUID PUENTE = id(1);
  private static final UUID OTRO = id(2);
  private static final UUID ADMIN = id(3);
  private static final UUID CATEDRATICO = id(4);
  private static final UUID ORDEN = id(10);
  private static final String JUSTIFICACION =
      "  Se prioriza la intervención por su urgencia observada.  ";
  private static final String CUERPO = cuerpo("BAJA", JUSTIFICACION);
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired OrdenMantenimientoService servicio;

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
    jdbc.update(
        "DELETE FROM auditoria WHERE entidad = 'orden_mantenimiento' AND entidad_id IN (SELECT id FROM orden_mantenimiento WHERE puente_id IN (?, ?))",
        PUENTE,
        OTRO);
    jdbc.update("DELETE FROM orden_mantenimiento WHERE puente_id IN (?, ?)", PUENTE, OTRO);
    jdbc.update("DELETE FROM inspeccion WHERE id = ?", id(8));
    jdbc.update("DELETE FROM formulario_version WHERE id = ?", id(7));
    jdbc.update("DELETE FROM puente WHERE id IN (?, ?)", PUENTE, OTRO);
    jdbc.update("DELETE FROM usuario WHERE id = ?", id(6));
    jdbc.update("DELETE FROM usuario WHERE correo LIKE '%@prioridad-prueba.com'");
    jdbc.update("DELETE FROM municipio WHERE codigo = '9494'");
    jdbc.update("DELETE FROM departamento WHERE codigo = '94'");
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ADMINISTRADOR", "CATEDRATICO"})
  void ajustaConActoresAcordadosConservaSugeridaYAuditaSinCambiarEstado(Rol rol) throws Exception {
    UUID usuario = rol == Rol.ADMINISTRADOR ? ADMIN : CATEDRATICO;
    mvc.perform(solicitud(PUENTE, ORDEN, CUERPO).with(como(rol, usuario)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.prioridad").value("BAJA"))
        .andExpect(jsonPath("$.prioridad_sugerida").value("MEDIA"))
        .andExpect(jsonPath("$.justificacion_prioridad").value(JUSTIFICACION))
        .andExpect(jsonPath("$.estado").value("PROPUESTA"))
        .andExpect(jsonPath("$.descripcion").value("Intervención ficticia"))
        .andExpect(jsonPath("$.elemento_ref").value("elemento-de-prueba"))
        .andExpect(jsonPath("$.inspeccion_id").value(id(8).toString()))
        .andExpect(jsonPath("$.fecha_programada").value("2026-10-25"))
        .andExpect(jsonPath("$.fecha_ejecucion").isEmpty());
    assertThat(
            jdbc.queryForMap(
                "SELECT usuario_id, accion, valor_anterior->>'prioridad' AS anterior, valor_nuevo->>'prioridad' AS nueva, valor_nuevo->>'justificacion_prioridad' AS justificacion, correlacion_id FROM auditoria WHERE entidad_id = ?",
                ORDEN))
        .containsEntry("usuario_id", usuario)
        .containsEntry("accion", "MODIFICAR")
        .containsEntry("anterior", "ALTA")
        .containsEntry("nueva", "BAJA")
        .containsEntry("justificacion", JUSTIFICACION)
        .hasEntrySatisfying("correlacion_id", valor -> assertThat(valor).isNotNull());
  }

  @ParameterizedTest
  @ValueSource(ints = {10, 11, 12})
  void permiteAjustarEnLosTresEstadosAbiertos(int numero) throws Exception {
    String estado =
        jdbc.queryForObject(
            "SELECT estado FROM orden_mantenimiento WHERE id = ?", String.class, id(numero));
    mvc.perform(solicitud(PUENTE, id(numero), CUERPO).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value(estado));
  }

  @ParameterizedTest
  @ValueSource(ints = {13, 14, 15})
  void estadosTerminalesConservanPrioridadInclusoAlPedirLaMisma(int numero) throws Exception {
    mvc.perform(solicitud(PUENTE, id(numero), CUERPO).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isConflict());
    mvc.perform(
            solicitud(PUENTE, id(numero), cuerpo("MEDIA", "Sin cambio"))
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isConflict());
    assertThat(
            jdbc.queryForObject(
                "SELECT prioridad FROM orden_mantenimiento WHERE id = ?", String.class, id(numero)))
        .isEqualTo("MEDIA");
    assertThat(auditorias()).isZero();
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void otrosRolesNoAjustanNiPorHttpNiPorServicio(Rol rol) throws Exception {
    UUID usuario = rol == Rol.ESTUDIANTE ? id(5) : id(6);
    mvc.perform(solicitud(PUENTE, ORDEN, CUERPO).with(como(rol, usuario)))
        .andExpect(status().isForbidden());
    autenticar(rol, usuario);
    assertThatThrownBy(
            () -> servicio.ajustarPrioridad(PUENTE, ORDEN, Prioridad.BAJA, JUSTIFICACION))
        .isInstanceOf(AccessDeniedException.class);
    comprobarSinCambios();
  }

  @Test
  void sinSesionNoAjustaNiPorHttpNiPorServicio() throws Exception {
    mvc.perform(solicitud(PUENTE, ORDEN, CUERPO)).andExpect(status().isUnauthorized());
    SecurityContextHolder.clearContext();
    assertThatThrownBy(
            () -> servicio.ajustarPrioridad(PUENTE, ORDEN, Prioridad.BAJA, JUSTIFICACION))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    comprobarSinCambios();
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"prioridad\":null}", "{\"prioridad\":\"URGENTE\"}"})
  void dtoInvalidoDevuelve400(String cuerpo) throws Exception {
    mvc.perform(solicitud(PUENTE, ORDEN, cuerpo).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isBadRequest());
    comprobarSinCambios();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"prioridad\":\"BAJA\"}",
        "{\"prioridad\":\"BAJA\",\"justificacion\":null}",
        "{\"prioridad\":\"BAJA\",\"justificacion\":\"\"}",
        "{\"prioridad\":\"BAJA\",\"justificacion\":\"   \"}"
      })
  void cambiarExigeJustificacionNoBlanca(String cuerpo) throws Exception {
    mvc.perform(solicitud(PUENTE, ORDEN, cuerpo).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isUnprocessableEntity());
    comprobarSinCambios();
  }

  @ParameterizedTest
  @CsvSource({"2,10", "1,99", "99,10"})
  void ordenAjenaOAusenteDevuelve404(int puente, int orden) throws Exception {
    mvc.perform(solicitud(id(puente), id(orden), CUERPO).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isNotFound());
    comprobarSinCambios();
  }

  @Test
  void uuidInvalidoDa400EIdentificadoresNulosNoInicianAjuste() throws Exception {
    mvc.perform(
            patch("/api/v1/puentes/no-uuid/ordenes-mantenimiento/" + ORDEN + "/prioridad")
                .with(como(Rol.ADMINISTRADOR, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO))
        .andExpect(status().isBadRequest());
    autenticar(Rol.ADMINISTRADOR, ADMIN);
    assertThatThrownBy(() -> servicio.ajustarPrioridad(null, ORDEN, Prioridad.BAJA, JUSTIFICACION))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> servicio.ajustarPrioridad(PUENTE, null, Prioridad.BAJA, JUSTIFICACION))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> servicio.ajustarPrioridad(PUENTE, ORDEN, null, JUSTIFICACION))
        .isInstanceOf(com.cunoc.puentes.common.NegocioException.class);
    comprobarSinCambios();
  }

  @Test
  void origenInconsistenteImpideAjustar() throws Exception {
    jdbc.update("UPDATE orden_mantenimiento SET inspeccion_id = ? WHERE id = ?", id(8), id(16));
    mvc.perform(solicitud(OTRO, id(16), CUERPO).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isUnprocessableEntity());
    assertThat(
            jdbc.queryForObject(
                "SELECT prioridad FROM orden_mantenimiento WHERE id = ?", String.class, id(16)))
        .isEqualTo("MEDIA");
    assertThat(auditorias()).isZero();
  }

  @Test
  void repeticionNoEditaJustificacionNiActualizadoNiGeneraAuditoria() throws Exception {
    var antes =
        jdbc.queryForMap(
            "SELECT actualizado_en, justificacion_prioridad FROM orden_mantenimiento WHERE id = ?",
            ORDEN);
    mvc.perform(
            solicitud(PUENTE, ORDEN, "{\"prioridad\":\"ALTA\"}")
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.justificacion_prioridad").value("Justificación previa"));
    mvc.perform(
            solicitud(PUENTE, ORDEN, cuerpo("ALTA", "Texto que no debe reemplazar el previo"))
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForMap(
                "SELECT actualizado_en, justificacion_prioridad FROM orden_mantenimiento WHERE id = ?",
                ORDEN))
        .isEqualTo(antes);
    assertThat(auditorias()).isZero();
  }

  @Test
  void volverALaSugeridaExigeOtraJustificacionYLaSugerenciaPermanece() throws Exception {
    mvc.perform(solicitud(PUENTE, ORDEN, CUERPO).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk());
    mvc.perform(
            solicitud(PUENTE, ORDEN, "{\"prioridad\":\"MEDIA\"}")
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            solicitud(PUENTE, ORDEN, cuerpo("MEDIA", "Se retoma la sugerida"))
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.prioridad_sugerida").value("MEDIA"))
        .andExpect(jsonPath("$.justificacion_prioridad").value("Se retoma la sugerida"));
    assertThat(auditorias()).isEqualTo(2);
  }

  @Test
  void sinSugerenciaNiInspeccionNoInventaUnaAlModificar() throws Exception {
    mvc.perform(
            solicitud(OTRO, id(16), cuerpo("ALTA", JUSTIFICACION))
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.prioridad_sugerida").isEmpty())
        .andExpect(jsonPath("$.inspeccion_id").isEmpty());
  }

  @Test
  void justificacionLargaSeGuardaYAuditaEnSuCampoText() throws Exception {
    String larga = "Justificación 🚧 ".repeat(100);
    mvc.perform(
            solicitud(PUENTE, ORDEN, cuerpo("BAJA", larga)).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.justificacion_prioridad").value(larga));
    assertThat(
            jdbc.queryForObject(
                "SELECT valor_nuevo->>'justificacion_prioridad' FROM auditoria WHERE entidad_id = ?",
                String.class,
                ORDEN))
        .isEqualTo(larga);
  }

  @Test
  void falloDeAuditoriaReviertePrioridadYJustificacion() {
    autenticar(Rol.ADMINISTRADOR, id(99));
    assertThatThrownBy(
            () -> servicio.ajustarPrioridad(PUENTE, ORDEN, Prioridad.BAJA, JUSTIFICACION))
        .isInstanceOf(DataIntegrityViolationException.class);
    comprobarSinCambios();
  }

  @Test
  void dosAjustesIgualesConcurrentesSoloCambianYAuditanUnaVez() throws Exception {
    ejecutarConcurrentes(Prioridad.BAJA, Prioridad.BAJA);
    assertThat(
            jdbc.queryForObject(
                "SELECT prioridad FROM orden_mantenimiento WHERE id = ?", String.class, ORDEN))
        .isEqualTo("BAJA");
    assertThat(auditorias()).isEqualTo(1);
  }

  @Test
  void ajustesDistintosConcurrentesDejanUnaCadenaCoherenteDeAuditoria() throws Exception {
    ejecutarConcurrentes(Prioridad.BAJA, Prioridad.MEDIA);
    var filas =
        jdbc.queryForList(
            "SELECT valor_anterior->>'prioridad' AS anterior, valor_nuevo->>'prioridad' AS nueva FROM auditoria WHERE entidad_id = ? ORDER BY creado_en, id",
            ORDEN);
    assertThat(filas).hasSize(2);
    assertThat(filas.getFirst().get("anterior")).isEqualTo("ALTA");
    assertThat(filas.getLast().get("anterior")).isEqualTo(filas.getFirst().get("nueva"));
    assertThat(
            jdbc.queryForObject(
                "SELECT prioridad FROM orden_mantenimiento WHERE id = ?", String.class, ORDEN))
        .isEqualTo(filas.getLast().get("nueva"));
  }

  private void ejecutarConcurrentes(Prioridad primera, Prioridad segunda) throws Exception {
    CountDownLatch listas = new CountDownLatch(2);
    CountDownLatch salida = new CountDownLatch(1);
    try (var ejecutor = Executors.newFixedThreadPool(2)) {
      var uno = ejecutor.submit(() -> ajustarConcurrente(ADMIN, primera, listas, salida));
      var dos = ejecutor.submit(() -> ajustarConcurrente(CATEDRATICO, segunda, listas, salida));
      boolean preparadas = listas.await(10, TimeUnit.SECONDS);
      salida.countDown();
      assertThat(preparadas).isTrue();
      assertThat(uno.get(10, TimeUnit.SECONDS).prioridad()).isEqualTo(primera);
      assertThat(dos.get(10, TimeUnit.SECONDS).prioridad()).isEqualTo(segunda);
    }
  }

  private com.cunoc.puentes.mantenimiento.dto.OrdenMantenimientoDTO ajustarConcurrente(
      UUID usuario, Prioridad prioridad, CountDownLatch listas, CountDownLatch salida)
      throws InterruptedException {
    try {
      autenticar(usuario.equals(ADMIN) ? Rol.ADMINISTRADOR : Rol.CATEDRATICO, usuario);
      listas.countDown();
      if (!salida.await(10, TimeUnit.SECONDS))
        throw new IllegalStateException("No se liberó el ajuste concurrente.");
      return servicio.ajustarPrioridad(PUENTE, ORDEN, prioridad, JUSTIFICACION);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private void comprobarSinCambios() {
    assertThat(
            jdbc.queryForMap(
                "SELECT prioridad, justificacion_prioridad FROM orden_mantenimiento WHERE id = ?",
                ORDEN))
        .containsEntry("prioridad", "ALTA")
        .containsEntry("justificacion_prioridad", "Justificación previa");
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

  private static String cuerpo(String prioridad, String justificacion) {
    return "{\"prioridad\":\"" + prioridad + "\",\"justificacion\":\"" + justificacion + "\"}";
  }

  private static MockHttpServletRequestBuilder solicitud(UUID puente, UUID orden, String cuerpo) {
    return patch("/api/v1/puentes/" + puente + "/ordenes-mantenimiento/" + orden + "/prioridad")
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private static UUID id(int n) {
    return UUID.fromString("019a0032-0000-7000-8000-%012d".formatted(n));
  }
}
