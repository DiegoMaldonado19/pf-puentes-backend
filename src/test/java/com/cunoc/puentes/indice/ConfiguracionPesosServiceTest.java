package com.cunoc.puentes.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.usuario.Rol;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * No usa transacción de prueba: comprueba commits y rollbacks reales del servicio y sus aspectos.
 */
@SpringBootTest
@Sql("/indice/servicio-pesos.sql")
class ConfiguracionPesosServiceTest extends PruebaIntegracion {

  private static final UUID FORMULARIO_A = UUID.fromString("019a0018-0000-7000-8000-000000000001");
  private static final UUID FORMULARIO_B = UUID.fromString("019a0018-0000-7000-8000-000000000002");
  private static final UUID ADMIN = UUID.fromString("019a0018-0000-7000-8000-000000000003");
  private static final UUID INEXISTENTE = UUID.fromString("019a0018-0000-7000-8000-000000000099");

  @Autowired ConfiguracionPesosService servicio;
  @Autowired JdbcTemplate jdbc;
  @MockitoSpyBean PesoElementoRepository pesos;

  @BeforeEach
  void autenticarAdministrador() {
    RequestContextHolder.resetRequestAttributes();
    autenticar(Rol.ADMINISTRADOR, ADMIN);
  }

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
    jdbc.update("DELETE FROM auditoria WHERE usuario_id = ?", ADMIN);
    jdbc.update(
        "DELETE FROM peso_elemento WHERE version_pesos_id IN"
            + " (SELECT id FROM version_pesos WHERE formulario_version_id IN (?, ?))",
        FORMULARIO_A,
        FORMULARIO_B);
    jdbc.update(
        "DELETE FROM version_pesos WHERE formulario_version_id IN (?, ?)",
        FORMULARIO_A,
        FORMULARIO_B);
    jdbc.update("DELETE FROM formulario_version WHERE id IN (?, ?)", FORMULARIO_A, FORMULARIO_B);
    jdbc.update("DELETE FROM usuario WHERE id = ?", ADMIN);
  }

  @Test
  void administradorCreaJuegoYPesosConAuditoriaDeCadaFilaEnLaMismaOperacion() {
    JuegoPesos definicion =
        new JuegoPesos(
            FORMULARIO_A,
            "acta-de-prueba.pdf",
            List.of(
                new PesoConfigurado("primero", new BigDecimal("1.250")),
                new PesoConfigurado("segundo", new BigDecimal("2.500"))));

    UUID id = servicio.crear(definicion, metadatos());

    assertThat(id.version()).isEqualTo(7);
    Map<String, Object> version = jdbc.queryForMap("SELECT * FROM version_pesos WHERE id = ?", id);
    assertThat(version)
        .containsEntry("formulario_version_id", FORMULARIO_A)
        .containsEntry("acta", "acta-de-prueba.pdf");
    assertThat(valor(id, "primero")).isEqualByComparingTo("1.250");
    assertThat(valor(id, "segundo")).isEqualByComparingTo("2.500");
    List<Map<String, Object>> bitacora =
        jdbc.queryForList("SELECT * FROM auditoria WHERE usuario_id = ?", ADMIN);
    assertThat(bitacora).hasSize(3);
    assertThat(bitacora)
        .extracting(fila -> fila.get("entidad"))
        .containsExactlyInAnyOrder("version_pesos", "peso_elemento", "peso_elemento");
    Object correlacion = bitacora.getFirst().get("correlacion_id");
    assertThat(correlacion).isNotNull();
    assertThat(bitacora)
        .allSatisfy(
            fila -> {
              assertThat(fila)
                  .containsEntry("usuario_id", ADMIN)
                  .containsEntry("accion", "CREAR")
                  .containsEntry("correlacion_id", correlacion);
              assertThat(fila.get("valor_anterior")).isNull();
              assertThat(fila.get("valor_nuevo")).isNotNull();
            });
  }

  @Test
  void crearOtroJuegoNoModificaLosPesosNiElActaDelAnterior() {
    UUID anterior =
        servicio.crear(
            new JuegoPesos(
                FORMULARIO_A,
                "acta-anterior-de-prueba.pdf",
                List.of(new PesoConfigurado("primero", BigDecimal.ONE))),
            metadatos());
    UUID nuevo =
        servicio.crear(
            new JuegoPesos(
                FORMULARIO_A,
                "acta-nueva-de-prueba.pdf",
                List.of(new PesoConfigurado("primero", new BigDecimal("3")))),
            metadatos());

    assertThat(nuevo).isNotEqualTo(anterior);
    assertThat(valor(anterior, "primero")).isEqualByComparingTo("1");
    assertThat(valor(nuevo, "primero")).isEqualByComparingTo("3");
    assertThat(
            jdbc.queryForMap("SELECT acta FROM version_pesos WHERE id = ?", anterior).get("acta"))
        .isEqualTo("acta-anterior-de-prueba.pdf");
    assertThat(juegosGuardados()).isEqualTo(2);
  }

  @ParameterizedTest
  @EnumSource(value = Rol.class, names = "ADMINISTRADOR", mode = EnumSource.Mode.EXCLUDE)
  void losOtrosRolesNoPuedenCrearJuegos(Rol rol) {
    autenticar(rol, ADMIN);

    assertThatThrownBy(() -> servicio.crear(juego("1"), metadatos()))
        .isInstanceOf(AccessDeniedException.class);
    comprobarSinCambios();
  }

  @Test
  void sinSesionNoSePuedeCrearUnJuego() {
    SecurityContextHolder.clearContext();

    assertThatThrownBy(() -> servicio.crear(juego("1"), metadatos()))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    comprobarSinCambios();
  }

  @Test
  void rechazaUnJuegoAusente() {
    assertThatIllegalArgumentException().isThrownBy(() -> servicio.crear(null, metadatos()));
    comprobarSinCambios();
  }

  @Test
  void rechazaMetadatosAusentes() {
    assertThatIllegalArgumentException().isThrownBy(() -> servicio.crear(juego("1"), null));
    comprobarSinCambios();
  }

  @Test
  void rechazaCamposDeOtraVersionAunqueLasReferenciasCoincidan() {
    comprobarError(
        HttpStatus.UNPROCESSABLE_CONTENT,
        () -> servicio.crear(juego("1"), new ElementosDeDano(FORMULARIO_B, Set.of("primero"))));
    comprobarSinCambios();
  }

  @Test
  void rechazaUnFormularioInexistenteAntesDeGuardar() {
    JuegoPesos juego =
        new JuegoPesos(INEXISTENTE, null, List.of(new PesoConfigurado("primero", BigDecimal.ONE)));

    comprobarError(
        HttpStatus.NOT_FOUND,
        () -> servicio.crear(juego, new ElementosDeDano(INEXISTENTE, Set.of("primero"))));
    comprobarSinCambios();
  }

  @Test
  void rechazaUnaReferenciaQueNoSeaCampoDeDanoSinGuardarParcialmente() {
    JuegoPesos juego =
        new JuegoPesos(
            FORMULARIO_A,
            null,
            List.of(
                new PesoConfigurado("primero", BigDecimal.ONE),
                new PesoConfigurado("dimension-permanente-de-prueba", BigDecimal.ONE)));

    comprobarError(HttpStatus.UNPROCESSABLE_CONTENT, () -> servicio.crear(juego, metadatos()));
    comprobarSinCambios();
  }

  @Test
  void revierteJuegoPesosYAuditoriaSiFallaElSegundoPeso() {
    doThrow(new DataIntegrityViolationException("Fallo de persistencia simulado"))
        .when(pesos)
        .save(argThat(peso -> peso != null && "segundo".equals(peso.getElementoRef())));
    JuegoPesos juego =
        new JuegoPesos(
            FORMULARIO_A,
            null,
            List.of(
                new PesoConfigurado("primero", BigDecimal.ONE),
                new PesoConfigurado("segundo", BigDecimal.ONE)));

    assertThatThrownBy(() -> servicio.crear(juego, metadatos()))
        .isInstanceOf(DataIntegrityViolationException.class);
    comprobarSinCambios();
  }

  @Test
  void unFalloDeAuditoriaTambienRevierteElJuegoYLosPesos() {
    // La bitácora rechaza un usuario inexistente por FK; el rol es válido para entrar al servicio.
    autenticar(Rol.ADMINISTRADOR, INEXISTENTE);

    assertThatThrownBy(() -> servicio.crear(juego("1"), metadatos()))
        .isInstanceOf(DataIntegrityViolationException.class);
    comprobarSinCambios();
  }

  private long juegosGuardados() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM version_pesos WHERE formulario_version_id IN (?, ?)",
        Long.class,
        FORMULARIO_A,
        FORMULARIO_B);
  }

  private void comprobarSinCambios() {
    assertThat(juegosGuardados()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM peso_elemento p JOIN version_pesos v"
                    + " ON p.version_pesos_id = v.id WHERE v.formulario_version_id IN (?, ?)",
                Long.class,
                FORMULARIO_A,
                FORMULARIO_B))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE usuario_id = ?", Long.class, ADMIN))
        .isZero();
  }

  private BigDecimal valor(UUID juegoId, String elemento) {
    return jdbc.queryForObject(
        "SELECT peso FROM peso_elemento WHERE version_pesos_id = ? AND elemento_ref = ?",
        BigDecimal.class,
        juegoId,
        elemento);
  }

  private static ElementosDeDano metadatos() {
    return new ElementosDeDano(FORMULARIO_A, Set.of("primero", "segundo"));
  }

  private static JuegoPesos juego(String peso) {
    return new JuegoPesos(
        FORMULARIO_A, null, List.of(new PesoConfigurado("primero", new BigDecimal(peso))));
  }

  private static void autenticar(Rol rol, UUID usuario) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                usuario.toString(),
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()))));
  }

  private static void comprobarError(HttpStatus estado, Runnable operacion) {
    assertThatThrownBy(operacion::run)
        .isInstanceOfSatisfying(
            NegocioException.class, error -> assertThat(error.getStatusCode()).isEqualTo(estado));
  }
}
