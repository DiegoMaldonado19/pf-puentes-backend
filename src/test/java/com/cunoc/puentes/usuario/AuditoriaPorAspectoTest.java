package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.AuditablesDePrueba.OperacionCompuesta;
import com.cunoc.puentes.usuario.AuditablesDePrueba.UsuariosAuditables;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-P2-03: el aspecto sobre servicios de prueba, con PostgreSQL real y transacciones reales (la
 * prueba no es transaccional: así se comprueba qué queda confirmado y qué se revierte).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({AuditablesDePrueba.class, AuditoriaPorAspectoTest.ControladorDeAuditoria.class})
class AuditoriaPorAspectoTest extends PruebaIntegracion {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UsuarioRepository usuarios;
  @Autowired UsuariosAuditables servicio;
  @Autowired OperacionCompuesta compuesta;

  private UUID administrador;
  private UUID ana;
  private Instant antes;

  @BeforeEach
  void crearUsuarios() {
    RequestContextHolder.resetRequestAttributes();
    antes = Instant.now().minusSeconds(1);
    administrador = usuarios.save(Cuentas.administrador(unico("admin"))).getId();
    Usuario cuenta = Cuentas.estudiante(unico("ana"));
    cuenta.setNombre("Ana");
    ana = usuarios.save(cuenta).getId();
  }

  @AfterEach
  void limpiar() {
    RequestContextHolder.resetRequestAttributes(); // MockMvc deja la petición en el hilo
    SecurityContextHolder.clearContext();
    jdbc.update(
        "DELETE FROM auditoria WHERE usuario_id = ? OR entidad_id IN"
            + " (SELECT id FROM usuario WHERE correo LIKE 'auditoria-%')",
        administrador);
  }

  @Test
  void registraQuienCuandoQueYSobreQue() throws Exception {
    mvc.perform(
            post(ruta(ana, "renombrar"))
                .param("nombre", "Ana María")
                .with(como(Rol.ADMINISTRADOR, administrador)))
        .andExpect(status().isNoContent());

    Map<String, Object> fila = unaFila(ana);
    assertThat(fila)
        .containsEntry("usuario_id", administrador)
        .containsEntry("accion", "MODIFICAR")
        .containsEntry("entidad", "usuario")
        .containsEntry("entidad_id", ana);
    // TIMESTAMPTZ: el instante absoluto es el de ahora, sin depender de la zona de la sesión
    assertThat(instante(fila.get("creado_en"))).isBetween(antes, Instant.now());
  }

  @Test
  void guardaLosValoresAnteriorYPosterior() {
    servicio.renombrar(ana, "Ana María");

    Map<String, Object> fila = unaFila(ana);
    assertThat(json(fila, "valor_anterior")).containsEntry("nombre", "Ana");
    assertThat(json(fila, "valor_nuevo")).containsEntry("nombre", "Ana María");
    // solo cambió el nombre (y la marca de actualización): el resto es igual
    assertThat(json(fila, "valor_nuevo")).containsEntry("rol", "ESTUDIANTE");
  }

  @Test
  void unaCreacionNoTieneValorAnteriorYDescribeLaCuentaCreada() {
    Usuario creada = servicio.crear(unico("nueva"));

    Map<String, Object> fila = unaFila(creada.getId());
    assertThat(fila).containsEntry("accion", "CREAR").containsEntry("entidad_id", creada.getId());
    assertThat(fila.get("valor_anterior")).isNull();
    assertThat(json(fila, "valor_nuevo"))
        .containsEntry("id", creada.getId().toString())
        .containsEntry("estado", "ACTIVO");
  }

  @Test
  void nuncaGuardaContrasenasNiTokensYEnmascaraElCorreo() {
    servicio.cambiarContrasena(ana, "$2a$12$nuevo-hash-que-no-debe-salir");
    Usuario creada = servicio.crear("auditoria-" + UUID.randomUUID() + "@correo.com");

    for (Map<String, Object> fila : List.of(unaFila(ana), unaFila(creada.getId()))) {
      for (String columna : List.of("valor_anterior", "valor_nuevo")) {
        Map<String, Object> valores = json(fila, columna);
        assertThat(valores).doesNotContainKeys("contrasena_hash", "token_hash");
        assertThat(valores.toString()).doesNotContain("nuevo-hash-que-no-debe-salir");
        assertThat(valores.keySet()).noneMatch(clave -> clave.contains("token"));
        if (valores.containsKey("correo")) {
          assertThat(valores.get("correo").toString()).matches("[a-z]\\*\\*\\*@correo\\.com");
        }
      }
    }
  }

  @Test
  void conCondicionFalsaElMetodoSeConfirmaPeroNoQuedaFila() {
    servicio.renombrarSiAudita(ana, "Sin fila", false);

    assertThat(usuarios.findById(ana).orElseThrow().getNombre()).isEqualTo("Sin fila");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad_id = ?", Integer.class, ana))
        .isZero();
  }

  @Test
  void conCondicionVerdaderaSiQuedaLaFila() {
    servicio.renombrarSiAudita(ana, "Con fila", true);

    assertThat(json(unaFila(ana), "valor_nuevo")).containsEntry("nombre", "Con fila");
  }

  @Test
  void siLaOperacionFallaNoQuedaRegistroNiCambio() {
    assertThatThrownBy(() -> servicio.renombrarYFallar(ana, "Nombre que no debe quedar"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("falla después de modificar");

    assertThat(filas(ana)).isEmpty();
    assertThat(nombreDe(ana)).isEqualTo("Ana");
  }

  @Test
  void siFallaLaBitacoraLaOperacionCompletaSeRevierteConUn500SinDetalles() throws Exception {
    UUID desconocido = UUID.randomUUID(); // la FK de auditoria.usuario_id lo rechaza

    mvc.perform(
            post(ruta(ana, "renombrar"))
                .param("nombre", "Nombre que no debe quedar")
                .with(como(Rol.ADMINISTRADOR, desconocido)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado."))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("usuario_id"))));

    assertThat(nombreDe(ana)).isEqualTo("Ana");
    assertThat(filas(ana)).isEmpty();
  }

  @Test
  void unaAccionDelSistemaSeGuardaSinUsuarioNiOrigen() {
    servicio.renombrar(ana, "Ana María");

    Map<String, Object> fila = unaFila(ana);
    assertThat(fila.get("usuario_id")).isNull();
    assertThat(fila.get("origen_ip")).isNull();
    assertThat(fila.get("correlacion_id")).isNotNull();
  }

  @Test
  void guardaElMotivoEnSuColumnaPropia() {
    servicio.darDeBaja(ana, "Retiro del curso");

    Map<String, Object> fila = unaFila(ana);
    assertThat(fila)
        .containsEntry("accion", "DAR_DE_BAJA")
        .containsEntry("motivo", "Retiro del curso");
    assertThat(json(fila, "valor_anterior")).containsEntry("estado", "ACTIVO");
    assertThat(json(fila, "valor_nuevo")).containsEntry("estado", "INACTIVO");
  }

  @Test
  void sinMotivoLaColumnaQuedaVacia() {
    servicio.renombrar(ana, "Ana María");

    assertThat(unaFila(ana).get("motivo")).isNull();
  }

  @Test
  void guardaLaIpRealDelClienteQueDejoElProxy() throws Exception {
    mvc.perform(
            post(ruta(ana, "renombrar"))
                .param("nombre", "Ana María")
                .with(como(Rol.ADMINISTRADOR, administrador))
                .with(
                    peticion -> {
                      peticion.setRemoteAddr("203.0.113.7"); // lo que deja RemoteIpValve
                      return peticion;
                    }))
        .andExpect(status().isNoContent());

    assertThat(unaFila(ana)).containsEntry("origen_ip", "203.0.113.7");
  }

  @Test
  void dosMetodosAuditablesDeUnaMismaPeticionComparteCorrelacion() throws Exception {
    mvc.perform(
            post(ruta(ana, "compuesta"))
                .param("nombre", "Ana María")
                .with(como(Rol.ADMINISTRADOR, administrador)))
        .andExpect(status().isNoContent());
    mvc.perform(
            post(ruta(ana, "renombrar"))
                .param("nombre", "Otra")
                .with(como(Rol.ADMINISTRADOR, administrador)))
        .andExpect(status().isNoContent());

    List<Map<String, Object>> filas = filas(ana);
    assertThat(filas)
        .extracting(f -> f.get("accion"))
        .containsExactly("MODIFICAR", "BLOQUEAR_CUENTA", "MODIFICAR");
    assertThat(filas.get(0).get("correlacion_id"))
        .isNotNull()
        .isEqualTo(filas.get(1).get("correlacion_id"))
        .isNotEqualTo(filas.get(2).get("correlacion_id"));
  }

  @Test
  void fueraDeHttpLasLlamadasDeUnaMismaOperacionComparteCorrelacion() {
    compuesta.renombrarYBloquearAuditado(ana, "Ana María");
    servicio.renombrar(ana, "Otra");

    // la llamada auditable más externa define la correlación de las que ella dispara
    List<Map<String, Object>> filas = filas(ana);
    assertThat(filas).hasSize(4);
    assertThat(filas.get(0).get("correlacion_id"))
        .isNotNull()
        .isEqualTo(filas.get(1).get("correlacion_id"))
        .isEqualTo(filas.get(2).get("correlacion_id"))
        .isNotEqualTo(filas.get(3).get("correlacion_id"));
  }

  @Test
  void losIntentosFallidosNoInundanLaBitacoraPeroElBloqueoSiQuedaRegistrado() {
    servicio.registrarIntentoFallido(ana);
    servicio.registrarIntentoFallido(ana);
    servicio.registrarIntentoFallido(ana);
    assertThat(filas(ana)).isEmpty();

    servicio.bloquear(ana);

    assertThat(filas(ana))
        .singleElement()
        .satisfies(fila -> assertThat(fila).containsEntry("accion", "BLOQUEAR_CUENTA"));
  }

  private static String ruta(UUID id, String operacion) {
    return "/api/v1/prueba/auditoria/%s/%s".formatted(id, operacion);
  }

  private static String unico(String prefijo) {
    return "auditoria-" + prefijo + "-" + UUID.randomUUID() + "@correo.com";
  }

  private Map<String, Object> unaFila(UUID entidadId) {
    assertThat(filas(entidadId)).hasSize(1);
    return filas(entidadId).getFirst();
  }

  private List<Map<String, Object>> filas(UUID entidadId) {
    return jdbc.queryForList(
        "SELECT *, valor_anterior::text AS anterior_json, valor_nuevo::text AS nuevo_json"
            + " FROM auditoria WHERE entidad_id = ? ORDER BY creado_en, id",
        entidadId);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> json(Map<String, Object> fila, String columna) {
    String texto =
        (String) fila.get(columna.equals("valor_anterior") ? "anterior_json" : "nuevo_json");
    return texto == null ? Map.of() : JSON.readValue(texto, Map.class);
  }

  private static Instant instante(Object marca) {
    return marca instanceof java.sql.Timestamp timestamp
        ? timestamp.toInstant()
        : ((java.time.OffsetDateTime) marca).toInstant();
  }

  private String nombreDe(UUID id) {
    return jdbc.queryForObject("SELECT nombre FROM usuario WHERE id = ?", String.class, id);
  }

  /**
   * Endpoints de prueba; anidado aquí para que el escaneo de componentes no lo registre en otros
   * tests.
   */
  @RestController
  static class ControladorDeAuditoria {

    private final UsuariosAuditables usuarios;
    private final OperacionCompuesta compuesta;

    ControladorDeAuditoria(UsuariosAuditables usuarios, OperacionCompuesta compuesta) {
      this.usuarios = usuarios;
      this.compuesta = compuesta;
    }

    @PostMapping("/api/v1/prueba/auditoria/{id}/renombrar")
    ResponseEntity<Void> renombrar(@PathVariable UUID id, @RequestParam String nombre) {
      usuarios.renombrar(id, nombre);
      return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/prueba/auditoria/{id}/compuesta")
    ResponseEntity<Void> compuesta(@PathVariable UUID id, @RequestParam String nombre) {
      compuesta.renombrarYBloquear(id, nombre);
      return ResponseEntity.noContent().build();
    }
  }
}
