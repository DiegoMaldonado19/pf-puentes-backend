package com.cunoc.puentes.mantenimiento;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
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
import org.springframework.web.context.request.RequestContextHolder;

/** Sin transacción de prueba: verifica commits, auditoría, rollback y concurrencia reales. */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/mantenimiento/descarte-ordenes.sql")
class DescarteOrdenTest extends PruebaIntegracion {

  private static final String MOTIVO = "Daño registrado por duplicado; se conserva para consulta.";
  private static final String CUERPO = "{\"motivo\":\"" + MOTIVO + "\"}";
  private static final UUID PUENTE = id(1);
  private static final UUID OTRO_PUENTE = id(2);
  private static final UUID ADMIN = id(3);
  private static final UUID CATEDRATICO = id(4);
  private static final UUID PROPUESTA = id(10);
  private static final UUID SIN_FECHA = id(16);
  private static final UUID INEXISTENTE = id(99);

  @Autowired MockMvc mvc;
  @Autowired OrdenMantenimientoService servicio;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
    jdbc.update(
        "DELETE FROM auditoria WHERE entidad = 'orden_mantenimiento' AND entidad_id IN"
            + " (SELECT id FROM orden_mantenimiento WHERE puente_id IN (?, ?))",
        PUENTE,
        OTRO_PUENTE);
    jdbc.update("DELETE FROM orden_mantenimiento WHERE puente_id IN (?, ?)", PUENTE, OTRO_PUENTE);
    jdbc.update("DELETE FROM inspeccion WHERE id = ?", id(8));
    jdbc.update("DELETE FROM formulario_version WHERE id = ?", id(7));
    jdbc.update("DELETE FROM puente WHERE id IN (?, ?)", PUENTE, OTRO_PUENTE);
    jdbc.update("DELETE FROM usuario WHERE id = ?", id(6));
    jdbc.update("DELETE FROM usuario WHERE correo LIKE '%@descarte-prueba.com'");
    jdbc.update("DELETE FROM municipio WHERE codigo = '9595'");
    jdbc.update("DELETE FROM departamento WHERE codigo = '95'");
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ADMINISTRADOR", "CATEDRATICO"})
  void descartaConRolConfirmadoYConservaOrigenPrioridadesYProgramacion(Rol rol) throws Exception {
    UUID usuario = rol == Rol.ADMINISTRADOR ? ADMIN : CATEDRATICO;

    mvc.perform(solicitud(PUENTE, PROPUESTA).with(como(rol, usuario)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(PROPUESTA.toString()))
        .andExpect(jsonPath("$.puente_id").value(PUENTE.toString()))
        .andExpect(jsonPath("$.inspeccion_id").value(id(8).toString()))
        .andExpect(jsonPath("$.estado").value("DESCARTADA"))
        .andExpect(jsonPath("$.motivo").value(MOTIVO))
        .andExpect(jsonPath("$.tipo").value("CORRECTIVO"))
        .andExpect(jsonPath("$.descripcion").value("Intervención ficticia"))
        .andExpect(jsonPath("$.prioridad_sugerida").value("MEDIA"))
        .andExpect(jsonPath("$.prioridad").value("ALTA"))
        .andExpect(jsonPath("$.justificacion_prioridad").value("Justificación previa"))
        .andExpect(jsonPath("$.fecha_programada").value("2026-10-25"))
        .andExpect(jsonPath("$.fecha_ejecucion").isEmpty())
        .andExpect(jsonPath("$.actualizado_en").isNotEmpty())
        .andExpect(jsonPath("$.puenteId").doesNotExist());

    assertThat(estado(PROPUESTA)).isEqualTo("DESCARTADA");
    assertThat(
            jdbc.queryForMap(
                "SELECT usuario_id, accion, valor_anterior->>'estado' AS anterior,"
                    + " valor_nuevo->>'estado' AS nuevo, valor_nuevo->>'motivo' AS motivo, correlacion_id, creado_en"
                    + " FROM auditoria WHERE entidad = 'orden_mantenimiento' AND entidad_id = ?",
                PROPUESTA))
        .containsEntry("usuario_id", usuario)
        .containsEntry("accion", "CAMBIAR_ESTADO")
        .containsEntry("anterior", "PROPUESTA")
        .containsEntry("nuevo", "DESCARTADA")
        .containsEntry("motivo", MOTIVO)
        .hasEntrySatisfying("correlacion_id", valor -> assertThat(valor).isNotNull())
        .hasEntrySatisfying("creado_en", valor -> assertThat(valor).isNotNull());
  }

  @Test
  void descartaSinInspeccionOpcionalYConservaProgramacionAusente() throws Exception {
    jdbc.update("UPDATE orden_mantenimiento SET estado = 'PROPUESTA' WHERE id = ?", SIN_FECHA);
    mvc.perform(solicitud(OTRO_PUENTE, SIN_FECHA).with(como(Rol.CATEDRATICO, CATEDRATICO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("DESCARTADA"))
        .andExpect(jsonPath("$.motivo").value(MOTIVO))
        .andExpect(jsonPath("$.fecha_programada").isEmpty())
        .andExpect(jsonPath("$.inspeccion_id").isEmpty());
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void otrosRolesNoDescartanNiAlteranLaOrden(Rol rol) throws Exception {
    mvc.perform(solicitud(PUENTE, PROPUESTA).with(como(rol, rol == Rol.ESTUDIANTE ? id(5) : id(6))))
        .andExpect(status().isForbidden());
    comprobarPropuestaSinAuditoria();
  }

  @Test
  void sinSesionResponde401() throws Exception {
    mvc.perform(solicitud(PUENTE, PROPUESTA))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    comprobarPropuestaSinAuditoria();
  }

  @ParameterizedTest
  @ValueSource(ints = {11, 12, 13, 14, 15})
  void soloUnaPropuestaPuedeDescartarseSinReabrirOrdenes(int numero) throws Exception {
    String anterior = estado(id(numero));
    mvc.perform(solicitud(PUENTE, id(numero)).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(409));
    assertThat(estado(id(numero))).isEqualTo(anterior);
    assertThat(auditorias()).isZero();
  }

  @ParameterizedTest
  @CsvSource({"2,10", "1,99", "99,10"})
  void ordenAusenteODeOtroPuenteResponde404(int puente, int orden) throws Exception {
    mvc.perform(solicitud(id(puente), id(orden)).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
    comprobarPropuestaSinAuditoria();
  }

  @Test
  void uuidInvalidoResponde400SinCambios() throws Exception {
    mvc.perform(
            post("/api/v1/puentes/no-uuid/ordenes-mantenimiento/" + PROPUESTA + "/descarte")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO)
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/puentes/" + PUENTE + "/ordenes-mantenimiento/no-uuid/descarte")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO)
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isBadRequest());
    comprobarPropuestaSinAuditoria();
  }

  @Test
  void inspeccionDeOtroPuenteImpideDescartarLaOrdenInconsistente() throws Exception {
    jdbc.update(
        "UPDATE orden_mantenimiento SET estado = 'PROPUESTA', inspeccion_id = ? WHERE id = ?",
        id(8),
        SIN_FECHA);

    mvc.perform(solicitud(OTRO_PUENTE, SIN_FECHA).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value(422));
    assertThat(estado(SIN_FECHA)).isEqualTo("PROPUESTA");
    assertThat(auditorias()).isZero();
  }

  @Test
  void repetirDescarteProduce409SinDuplicarLaAuditoria() throws Exception {
    mvc.perform(solicitud(PUENTE, PROPUESTA).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk());
    mvc.perform(solicitud(PUENTE, PROPUESTA).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isConflict());
    assertThat(auditorias()).isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void elServicioTambienRechazaRolesSinPermiso(Rol rol) {
    autenticar(rol, rol == Rol.ESTUDIANTE ? id(5) : id(6));
    assertThatThrownBy(() -> servicio.descartar(PUENTE, PROPUESTA, MOTIVO))
        .isInstanceOf(AccessDeniedException.class);
    comprobarPropuestaSinAuditoria();
  }

  @Test
  void elServicioRechazaAusenciaDeSesionYDeIdentificadores() {
    SecurityContextHolder.clearContext();
    assertThatThrownBy(() -> servicio.descartar(PUENTE, PROPUESTA, MOTIVO))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    autenticar(Rol.ADMINISTRADOR, ADMIN);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> servicio.descartar(null, PROPUESTA, MOTIVO));
    assertThatIllegalArgumentException().isThrownBy(() -> servicio.descartar(PUENTE, null, MOTIVO));
    comprobarPropuestaSinAuditoria();
  }

  @Test
  void falloDeAuditoriaRevierteDescarteYMotivo() {
    // Una identidad inexistente pasa el rol en esta llamada Java, pero falla la FK de auditoría.
    autenticar(Rol.ADMINISTRADOR, INEXISTENTE);
    assertThatThrownBy(() -> servicio.descartar(PUENTE, PROPUESTA, MOTIVO))
        .isInstanceOf(DataIntegrityViolationException.class);
    comprobarPropuestaSinAuditoria();
  }

  @Test
  void dosDescartesConcurrentesSoloConfirmanUnaTransicionYUnaAuditoria() throws Exception {
    CountDownLatch listas = new CountDownLatch(2);
    CountDownLatch inicio = new CountDownLatch(1);
    try (var ejecutor = Executors.newFixedThreadPool(2)) {
      var primera = ejecutor.submit(() -> descartarConcurrente(ADMIN, listas, inicio));
      var segunda = ejecutor.submit(() -> descartarConcurrente(CATEDRATICO, listas, inicio));
      boolean preparadas = listas.await(10, TimeUnit.SECONDS);
      inicio.countDown();
      assertThat(preparadas).isTrue();
      assertThat(List.of(primera.get(10, TimeUnit.SECONDS), segunda.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    assertThat(estado(PROPUESTA)).isEqualTo("DESCARTADA");
    assertThat(auditorias()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"motivo\":null}", "{\"motivo\":\"\"}", "{\"motivo\":\"   \"}"})
  void motivoAusenteONoEscritoDevuelve400SinCambios(String cuerpo) throws Exception {
    mvc.perform(
            post(ruta(PUENTE, PROPUESTA))
                .with(como(Rol.ADMINISTRADOR, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
        .andExpect(status().isBadRequest());
    comprobarPropuestaSinAuditoria();
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.NullAndEmptySource
  @ValueSource(strings = {" ", "\t", "\u2003"})
  void elDominioTambienRechazaMotivoNoEscritoDesdeJava(String motivo) {
    autenticar(Rol.ADMINISTRADOR, ADMIN);
    assertThatThrownBy(() -> servicio.descartar(PUENTE, PROPUESTA, motivo))
        .isInstanceOf(NegocioException.class);
    comprobarPropuestaSinAuditoria();
  }

  @Test
  void conservaMotivoLargoExactoYLaOrdenSigueConsultableFueraDePendientes() throws Exception {
    String motivo = "  " + "Motivo 🚧 ".repeat(100) + "  ";
    mvc.perform(
            post(ruta(PUENTE, PROPUESTA))
                .with(como(Rol.CATEDRATICO, CATEDRATICO))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"" + motivo + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.motivo").value(motivo));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/puentes/" + PUENTE + "/ordenes-mantenimiento/" + PROPUESTA)
                .with(como(Rol.CATEDRATICO, CATEDRATICO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("DESCARTADA"))
        .andExpect(jsonPath("$.motivo").value(motivo));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/puentes/" + PUENTE + "/ordenes-mantenimiento/propuestas")
                .with(como(Rol.CATEDRATICO, CATEDRATICO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    assertThat(
            jdbc.queryForObject(
                "SELECT motivo FROM orden_mantenimiento WHERE id = ?", String.class, PROPUESTA))
        .isEqualTo(motivo);
  }

  @Test
  void aceptarYDescartarConcurrentementeConfirmanUnaSolaDecision() throws Exception {
    CountDownLatch listas = new CountDownLatch(2);
    CountDownLatch salida = new CountDownLatch(1);
    try (var ejecutor = Executors.newFixedThreadPool(2)) {
      var aceptar = ejecutor.submit(() -> decisionConcurrente(true, listas, salida));
      var descartar = ejecutor.submit(() -> decisionConcurrente(false, listas, salida));
      boolean preparadas = listas.await(10, TimeUnit.SECONDS);
      salida.countDown();
      assertThat(preparadas).isTrue();
      assertThat(List.of(aceptar.get(10, TimeUnit.SECONDS), descartar.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    assertThat(estado(PROPUESTA)).isIn("PROGRAMADA", "DESCARTADA");
    assertThat(auditorias()).isEqualTo(1);
  }

  private int decisionConcurrente(boolean aceptar, CountDownLatch listas, CountDownLatch salida)
      throws InterruptedException {
    try {
      autenticar(Rol.ADMINISTRADOR, ADMIN);
      listas.countDown();
      if (!salida.await(10, TimeUnit.SECONDS))
        throw new IllegalStateException("No se liberó la decisión.");
      if (aceptar) servicio.aceptar(PUENTE, PROPUESTA);
      else servicio.descartar(PUENTE, PROPUESTA, MOTIVO);
      return 200;
    } catch (NegocioException error) {
      return error.getStatusCode().value();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      solicitud(UUID puente, UUID orden) {
    return post(ruta(puente, orden)).contentType(MediaType.APPLICATION_JSON).content(CUERPO);
  }

  private int descartarConcurrente(UUID usuario, CountDownLatch listas, CountDownLatch inicio)
      throws InterruptedException {
    try {
      autenticar(usuario.equals(ADMIN) ? Rol.ADMINISTRADOR : Rol.CATEDRATICO, usuario);
      listas.countDown();
      if (!inicio.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("La prueba no liberó el inicio concurrente.");
      }
      servicio.descartar(PUENTE, PROPUESTA, MOTIVO);
      return 200;
    } catch (NegocioException error) {
      return error.getStatusCode().value();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private void comprobarPropuestaSinAuditoria() {
    assertThat(estado(PROPUESTA)).isEqualTo("PROPUESTA");
    assertThat(
            jdbc.queryForObject(
                "SELECT motivo FROM orden_mantenimiento WHERE id = ?", String.class, PROPUESTA))
        .isNull();
    assertThat(auditorias()).isZero();
  }

  private String estado(UUID orden) {
    return jdbc.queryForObject(
        "SELECT estado FROM orden_mantenimiento WHERE id = ?", String.class, orden);
  }

  private long auditorias() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM auditoria WHERE entidad = 'orden_mantenimiento'"
            + " AND entidad_id IN (SELECT id FROM orden_mantenimiento WHERE puente_id IN (?, ?))",
        Long.class,
        PUENTE,
        OTRO_PUENTE);
  }

  private static void autenticar(Rol rol, UUID usuario) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                usuario.toString(),
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()))));
  }

  private static UUID id(int numero) {
    return UUID.fromString("019a0031-0000-7000-8000-%012d".formatted(numero));
  }

  private static String ruta(UUID puente, UUID orden) {
    return "/api/v1/puentes/" + puente + "/ordenes-mantenimiento/" + orden + "/descarte";
  }
}
