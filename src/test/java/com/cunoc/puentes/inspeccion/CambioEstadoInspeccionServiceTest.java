package com.cunoc.puentes.inspeccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.indice.EstadoCondicion;
import com.cunoc.puentes.usuario.Rol;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.context.request.RequestContextHolder;

@SpringBootTest
@Sql("/inspeccion/maquina.sql")
class CambioEstadoInspeccionServiceTest extends PruebaIntegracion {

  private static final UUID PUENTE = id(1);
  private static final UUID ESTUDIANTE = id(11);
  private static final UUID REVISOR = id(13);
  private static final UUID CATEDRATICO = id(14);
  private static final UUID PROFESIONAL = id(15);
  private static final UUID OTRO = id(16);
  private static final UUID FORMULARIO = id(21);
  private static final UUID INEXISTENTE = id(99);
  private static final Instant HORA = Instant.parse("2026-10-24T20:00:00Z");

  @Autowired CambioEstadoInspeccionService servicio;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
    jdbc.update(
        "DELETE FROM auditoria WHERE entidad = 'inspeccion' AND entidad_id IN"
            + " (SELECT id FROM inspeccion WHERE puente_id = ?)",
        PUENTE);
    jdbc.update(
        "DELETE FROM transicion_inspeccion WHERE inspeccion_id IN"
            + " (SELECT id FROM inspeccion WHERE puente_id = ?)",
        PUENTE);
    jdbc.update(
        "DELETE FROM revision WHERE inspeccion_id IN (SELECT id FROM inspeccion WHERE puente_id = ?)",
        PUENTE);
    jdbc.update("DELETE FROM inspeccion WHERE puente_id = ?", PUENTE);
    jdbc.update("DELETE FROM formulario_version WHERE id = ?", FORMULARIO);
    jdbc.update("DELETE FROM usuario WHERE id = ?", PROFESIONAL);
    jdbc.update(
        "DELETE FROM usuario WHERE id IN (?, ?, ?, ?)", ESTUDIANTE, REVISOR, CATEDRATICO, OTRO);
    jdbc.update("DELETE FROM puente WHERE id = ?", PUENTE);
  }

  @ParameterizedTest
  @CsvSource({
    "BORRADOR, ENVIADA, 11, ESTUDIANTE",
    "ENVIADA, EN_REVISION, 13, CATEDRATICO",
    "ENVIADA, PUBLICADA, 14, CATEDRATICO",
    "ENVIADA, PUBLICADA, 15, PROFESIONAL_EXTERNO",
    "EN_REVISION, PUBLICADA, 13, CATEDRATICO",
    "EN_REVISION, RECHAZADA, 13, CATEDRATICO"
  })
  void transicionValidaConSuActorDejaEstadoHistorialYAuditoria(
      EstadoInspeccion origen, EstadoInspeccion destino, int actor, Rol rol) {
    UUID autor =
        destino == EstadoInspeccion.PUBLICADA && origen == EstadoInspeccion.ENVIADA
            ? id(actor)
            : ESTUDIANTE;
    UUID inspeccion = insertar(origen, autor);
    UUID revision =
        destino == EstadoInspeccion.RECHAZADA || origen == EstadoInspeccion.EN_REVISION
            ? ciclo(inspeccion, REVISOR)
            : null;
    autenticar(id(actor), rol);
    servicio.cambiar(inspeccion, destino, id(actor), datos(destino, revision));

    assertThat(estado(inspeccion)).isEqualTo(destino.name());
    assertThat(historial(inspeccion))
        .containsExactly(origen.name() + "->" + destino.name() + ":" + id(actor));
    assertThat(auditorias(inspeccion)).isEqualTo(1);
    assertThat(usuarioDeAuditoria(inspeccion)).isEqualTo(id(actor));
  }

  @Test
  void rechazaIdentificadoresNulos() {
    assertThatThrownBy(
            () ->
                servicio.cambiar(
                    null,
                    EstadoInspeccion.ENVIADA,
                    ESTUDIANTE,
                    DatosTransicionInspeccion.de(null, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void elSistemaPuedeDevolverABorradorSinSesion() {
    UUID inspeccion = insertar(EstadoInspeccion.CAMBIOS_SOLICITADOS, ESTUDIANTE);
    SecurityContextHolder.clearContext();
    servicio.cambiar(
        inspeccion, EstadoInspeccion.BORRADOR, null, DatosTransicionInspeccion.de(null, null));
    assertThat(estado(inspeccion)).isEqualTo("BORRADOR");
    assertThat(historial(inspeccion)).containsExactly("CAMBIOS_SOLICITADOS->BORRADOR:null");
  }

  @Test
  void soloElSistemaDevuelveDeCambiosSolicitadosABorrador() {
    UUID inspeccion = insertar(EstadoInspeccion.CAMBIOS_SOLICITADOS, ESTUDIANTE);
    autenticar(ESTUDIANTE, Rol.ESTUDIANTE);
    assertThatThrownBy(
            () ->
                servicio.cambiar(
                    inspeccion,
                    EstadoInspeccion.BORRADOR,
                    ESTUDIANTE,
                    DatosTransicionInspeccion.de(null, null)))
        .isInstanceOfSatisfying(
            NegocioException.class, error -> comprobar(error, 403, "accion-no-autorizada"));
  }

  @Test
  void cambiosSolicitadosVuelvenABorradorConHistorialDelSistema() {
    UUID inspeccion = insertar(EstadoInspeccion.EN_REVISION, ESTUDIANTE);
    UUID revision = ciclo(inspeccion, REVISOR);
    autenticar(REVISOR, Rol.CATEDRATICO);
    servicio.cambiar(
        inspeccion,
        EstadoInspeccion.CAMBIOS_SOLICITADOS,
        REVISOR,
        new DatosTransicionInspeccion(HORA, "corregir estribo", revision, null));

    assertThat(estado(inspeccion)).isEqualTo("BORRADOR");
    assertThat(historial(inspeccion))
        .containsExactly(
            "EN_REVISION->CAMBIOS_SOLICITADOS:" + REVISOR, "CAMBIOS_SOLICITADOS->BORRADOR:null");
    assertThat(
            jdbc.queryForObject(
                "SELECT motivo FROM transicion_inspeccion WHERE inspeccion_id = ? AND estado_destino = 'CAMBIOS_SOLICITADOS'",
                String.class,
                inspeccion))
        .isEqualTo("corregir estribo");
    assertThat(auditorias(inspeccion)).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad_id = ? AND usuario_id IS NULL",
                Long.class,
                inspeccion))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad_id = ? AND usuario_id = ?",
                Long.class,
                inspeccion,
                REVISOR))
        .isEqualTo(1L);
    autenticar(ESTUDIANTE, Rol.ESTUDIANTE);
    servicio.cambiar(
        inspeccion, EstadoInspeccion.ENVIADA, ESTUDIANTE, DatosTransicionInspeccion.de(null, null));
    assertThat(estado(inspeccion)).isEqualTo("ENVIADA");
  }

  @ParameterizedTest
  @CsvSource({
    "BORRADOR, PUBLICADA",
    "BORRADOR, EN_REVISION",
    "ENVIADA, BORRADOR",
    "EN_REVISION, BORRADOR",
    "PUBLICADA, BORRADOR",
    "PUBLICADA, RECHAZADA",
    "RECHAZADA, BORRADOR",
    "RECHAZADA, PUBLICADA",
    "CAMBIOS_SOLICITADOS, ENVIADA"
  })
  void todaTransicionInvalidaResponde409SinCambiarHistorial(
      EstadoInspeccion origen, EstadoInspeccion destino) {
    UUID inspeccion = insertar(origen, ESTUDIANTE);
    autenticar(ESTUDIANTE, Rol.ESTUDIANTE);
    assertThatThrownBy(
            () ->
                servicio.cambiar(
                    inspeccion, destino, ESTUDIANTE, DatosTransicionInspeccion.de("x", null)))
        .isInstanceOfSatisfying(
            NegocioException.class, error -> comprobar(error, 409, "transicion-invalida"));
    assertThat(estado(inspeccion)).isEqualTo(origen.name());
    assertThat(historial(inspeccion)).isEmpty();
    assertThat(auditorias(inspeccion)).isZero();
  }

  @Test
  void soloElAutorEnviaSuInspeccion() {
    UUID inspeccion = insertar(EstadoInspeccion.BORRADOR, ESTUDIANTE);
    autenticar(OTRO, Rol.ESTUDIANTE);
    assertThatThrownBy(
            () ->
                servicio.cambiar(
                    inspeccion,
                    EstadoInspeccion.ENVIADA,
                    OTRO,
                    DatosTransicionInspeccion.de(null, null)))
        .isInstanceOfSatisfying(
            NegocioException.class, error -> comprobar(error, 404, "inspeccion-no-encontrada"));
    assertThat(estado(inspeccion)).isEqualTo("BORRADOR");
  }

  @Test
  void elRevisorNoPuedeSerElAutor() {
    UUID inspeccion = insertar(EstadoInspeccion.ENVIADA, CATEDRATICO);
    autenticar(CATEDRATICO, Rol.CATEDRATICO);
    assertThatThrownBy(
            () ->
                servicio.cambiar(
                    inspeccion,
                    EstadoInspeccion.EN_REVISION,
                    CATEDRATICO,
                    DatosTransicionInspeccion.de(null, null)))
        .isInstanceOfSatisfying(
            NegocioException.class, error -> comprobar(error, 403, "revisor-es-el-autor"));
  }

  @Test
  void unEstudianteNuncaPublicaPorSuCuenta() {
    UUID inspeccion = insertar(EstadoInspeccion.ENVIADA, ESTUDIANTE);
    autenticar(ESTUDIANTE, Rol.ESTUDIANTE);
    assertThatThrownBy(
            () ->
                servicio.cambiar(
                    inspeccion, EstadoInspeccion.PUBLICADA, ESTUDIANTE, publicacion(null)))
        .isInstanceOfSatisfying(
            NegocioException.class,
            error -> comprobar(error, 403, "publicacion-directa-no-permitida"));
    assertThat(estado(inspeccion)).isEqualTo("ENVIADA");
  }

  @Test
  void elRechazoQuedaLigadoAlCicloDeRevision() {
    UUID inspeccion = insertar(EstadoInspeccion.EN_REVISION, ESTUDIANTE);
    UUID revision = ciclo(inspeccion, REVISOR);
    autenticar(REVISOR, Rol.CATEDRATICO);
    servicio.cambiar(
        inspeccion,
        EstadoInspeccion.RECHAZADA,
        REVISOR,
        new DatosTransicionInspeccion(HORA, "no cumple SIECA", revision, null));
    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT revision_id, motivo, actor_id FROM transicion_inspeccion WHERE inspeccion_id = ?",
            inspeccion);
    assertThat(fila.get("revision_id")).isEqualTo(revision);
    assertThat(fila.get("motivo")).isEqualTo("no cumple SIECA");
    assertThat(fila.get("actor_id")).isEqualTo(REVISOR);
  }

  @Test
  void seRegistranLasFechasDeEnvioYPublicacion() {
    UUID inspeccion = insertar(EstadoInspeccion.BORRADOR, CATEDRATICO);
    autenticar(CATEDRATICO, Rol.CATEDRATICO);
    servicio.cambiar(
        inspeccion,
        EstadoInspeccion.ENVIADA,
        CATEDRATICO,
        new DatosTransicionInspeccion(HORA, null, null, null));
    servicio.cambiar(inspeccion, EstadoInspeccion.PUBLICADA, CATEDRATICO, publicacion(null));
    Instant enviada =
        jdbc.queryForObject(
            "SELECT enviada_en FROM inspeccion WHERE id = ?", Instant.class, inspeccion);
    Instant publicada =
        jdbc.queryForObject(
            "SELECT publicada_en FROM inspeccion WHERE id = ?", Instant.class, inspeccion);
    assertThat(enviada).isEqualTo(HORA);
    assertThat(publicada).isEqualTo(HORA.plusSeconds(30));
  }

  @Test
  void unaInspeccionEliminadaNoCambiaDeEstado() {
    UUID inspeccion = insertar(EstadoInspeccion.BORRADOR, ESTUDIANTE);
    jdbc.update("UPDATE inspeccion SET eliminado_en = now() WHERE id = ?", inspeccion);
    autenticar(ESTUDIANTE, Rol.ESTUDIANTE);
    assertThatThrownBy(
            () ->
                servicio.cambiar(
                    inspeccion,
                    EstadoInspeccion.ENVIADA,
                    ESTUDIANTE,
                    DatosTransicionInspeccion.de(null, null)))
        .isInstanceOfSatisfying(
            NegocioException.class, error -> comprobar(error, 404, "inspeccion-no-encontrada"));
  }

  @Test
  void falloDeAuditoriaRevierteElEstadoYElHistorial() {
    UUID inspeccion = insertar(EstadoInspeccion.BORRADOR, ESTUDIANTE);
    autenticar(INEXISTENTE, Rol.ESTUDIANTE);
    assertThatThrownBy(
            () ->
                servicio.cambiar(
                    inspeccion,
                    EstadoInspeccion.ENVIADA,
                    ESTUDIANTE,
                    DatosTransicionInspeccion.de(null, null)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(estado(inspeccion)).isEqualTo("BORRADOR");
    assertThat(historial(inspeccion)).isEmpty();
    assertThat(auditorias(inspeccion)).isZero();
  }

  @Test
  void falloAlInsertarHistorialRevierteElEstado() {
    UUID inspeccion = insertar(EstadoInspeccion.EN_REVISION, ESTUDIANTE);
    autenticar(REVISOR, Rol.CATEDRATICO);
    assertThatThrownBy(
            () ->
                servicio.cambiar(
                    inspeccion,
                    EstadoInspeccion.RECHAZADA,
                    REVISOR,
                    new DatosTransicionInspeccion(HORA, "rechazo", INEXISTENTE, null)))
        .isInstanceOfAny(DataIntegrityViolationException.class, ConstraintViolationException.class);
    assertThat(estado(inspeccion)).isEqualTo("EN_REVISION");
    assertThat(historial(inspeccion)).isEmpty();
  }

  @Test
  void dosCambiosSimultaneosNoSePisan() throws Exception {
    UUID inspeccion = insertar(EstadoInspeccion.ENVIADA, ESTUDIANTE);
    CountDownLatch listas = new CountDownLatch(2);
    CountDownLatch inicio = new CountDownLatch(1);
    try (var ejecutor = Executors.newFixedThreadPool(2)) {
      var primera = ejecutor.submit(() -> abrir(inspeccion, REVISOR, listas, inicio));
      var segunda = ejecutor.submit(() -> abrir(inspeccion, CATEDRATICO, listas, inicio));
      assertThat(listas.await(10, TimeUnit.SECONDS)).isTrue();
      inicio.countDown();
      assertThat(List.of(primera.get(10, TimeUnit.SECONDS), segunda.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    assertThat(estado(inspeccion)).isEqualTo("EN_REVISION");
    assertThat(historial(inspeccion)).hasSize(1);
    assertThat(auditorias(inspeccion)).isEqualTo(1);
  }

  @Test
  void laAuditoriaGuardaEstadosAnteriorYPosterior() {
    UUID inspeccion = insertar(EstadoInspeccion.BORRADOR, ESTUDIANTE);
    autenticar(ESTUDIANTE, Rol.ESTUDIANTE);
    servicio.cambiar(
        inspeccion, EstadoInspeccion.ENVIADA, ESTUDIANTE, DatosTransicionInspeccion.de(null, null));
    String anterior =
        jdbc.queryForObject(
            "SELECT valor_anterior->>'estado' FROM auditoria WHERE entidad_id = ?",
            String.class,
            inspeccion);
    String posterior =
        jdbc.queryForObject(
            "SELECT valor_nuevo->>'estado' FROM auditoria WHERE entidad_id = ?",
            String.class,
            inspeccion);
    assertThat(anterior).isEqualTo("BORRADOR");
    assertThat(posterior).isEqualTo("ENVIADA");
  }

  private int abrir(UUID inspeccion, UUID actor, CountDownLatch listas, CountDownLatch inicio)
      throws InterruptedException {
    try {
      autenticar(actor, Rol.CATEDRATICO);
      listas.countDown();
      if (!inicio.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("La prueba no liberó el inicio concurrente.");
      }
      servicio.cambiar(
          inspeccion,
          EstadoInspeccion.EN_REVISION,
          actor,
          DatosTransicionInspeccion.de(null, null));
      return 200;
    } catch (NegocioException error) {
      return error.getStatusCode().value();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private UUID insertar(EstadoInspeccion estado, UUID autor) {
    UUID inspeccion = UUID.randomUUID();
    if (estado == EstadoInspeccion.PUBLICADA) {
      jdbc.update(
          """
          INSERT INTO inspeccion (id, puente_id, autor_id, formulario_version_id, fecha_inspeccion, estado,
            indice_condicion, estado_calculado, estado_confirmado, publicada_en, enviada_en)
          VALUES (?, ?, ?, ?, DATE '2026-10-01', 'PUBLICADA', 80, 'BUENO', 'BUENO', now(), now())
          """,
          inspeccion,
          PUENTE,
          autor,
          FORMULARIO);
    } else if (estado == EstadoInspeccion.RECHAZADA) {
      jdbc.update(
          """
          INSERT INTO inspeccion (id, puente_id, autor_id, formulario_version_id, fecha_inspeccion, estado)
          VALUES (?, ?, ?, ?, DATE '2026-10-01', 'RECHAZADA')
          """,
          inspeccion,
          PUENTE,
          autor,
          FORMULARIO);
    } else {
      jdbc.update(
          """
          INSERT INTO inspeccion (id, puente_id, autor_id, formulario_version_id, fecha_inspeccion, estado)
          VALUES (?, ?, ?, ?, DATE '2026-10-01', ?)
          """,
          inspeccion,
          PUENTE,
          autor,
          FORMULARIO,
          estado.name());
    }
    return inspeccion;
  }

  private UUID ciclo(UUID inspeccion, UUID revisor) {
    UUID revision = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO revision (id, inspeccion_id, revisor_id) VALUES (?, ?, ?)",
        revision,
        inspeccion,
        revisor);
    return revision;
  }

  private DatosTransicionInspeccion datos(EstadoInspeccion destino, UUID revision) {
    if (destino == EstadoInspeccion.PUBLICADA) {
      return publicacion(revision);
    }
    if (destino == EstadoInspeccion.RECHAZADA || destino == EstadoInspeccion.CAMBIOS_SOLICITADOS) {
      return new DatosTransicionInspeccion(HORA, "observación de revisión", revision, null);
    }
    return new DatosTransicionInspeccion(HORA, null, revision, null);
  }

  private static DatosTransicionInspeccion publicacion(UUID revision) {
    return new DatosTransicionInspeccion(
        HORA.plusSeconds(30),
        null,
        revision,
        new DatosTransicionInspeccion.Publicacion(
            new BigDecimal("80.00"),
            EstadoCondicion.BUENO,
            EstadoCondicion.BUENO,
            null,
            null,
            null,
            null));
  }

  private String estado(UUID inspeccion) {
    return jdbc.queryForObject(
        "SELECT estado FROM inspeccion WHERE id = ?", String.class, inspeccion);
  }

  private List<String> historial(UUID inspeccion) {
    return jdbc.query(
        """
        SELECT coalesce(estado_origen, 'null') || '->' || estado_destino || ':' || coalesce(actor_id::text, 'null')
        FROM transicion_inspeccion WHERE inspeccion_id = ? ORDER BY creado_en, id
        """,
        (rs, n) -> rs.getString(1),
        inspeccion);
  }

  private long auditorias(UUID inspeccion) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM auditoria WHERE entidad = 'inspeccion' AND entidad_id = ?",
        Long.class,
        inspeccion);
  }

  private UUID usuarioDeAuditoria(UUID inspeccion) {
    return jdbc.queryForObject(
        "SELECT usuario_id FROM auditoria WHERE entidad_id = ?", UUID.class, inspeccion);
  }

  private static void comprobar(NegocioException error, int estado, String tipo) {
    assertThat(error.getStatusCode().value()).isEqualTo(estado);
    assertThat(error.getBody().getType().toString())
        .isEqualTo("https://sgp.cunoc.usac.edu.gt/errores/" + tipo);
  }

  private static void autenticar(UUID usuario, Rol rol) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                usuario.toString(),
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()))));
  }

  private static UUID id(int numero) {
    return UUID.fromString("0199c100-0000-7000-8000-%012d".formatted(numero));
  }
}
