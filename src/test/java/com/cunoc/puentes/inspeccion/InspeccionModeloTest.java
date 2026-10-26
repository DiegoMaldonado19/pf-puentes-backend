package com.cunoc.puentes.inspeccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.indice.EstadoCondicion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@Sql("/inspeccion/modelo.sql")
class InspeccionModeloTest extends PruebaIntegracion {

  static final UUID PUENTE_A = UUID.fromString("0199c000-0000-7000-8000-000000000001");
  static final UUID PUENTE_B = UUID.fromString("0199c000-0000-7000-8000-000000000002");
  static final UUID AUTOR = UUID.fromString("0199c000-0000-7000-8000-000000000011");
  static final UUID ACOMPANANTE = UUID.fromString("0199c000-0000-7000-8000-000000000012");
  static final UUID FORMULARIO = UUID.fromString("0199c000-0000-7000-8000-000000000021");
  static final UUID CURSO = UUID.fromString("0199c000-0000-7000-8000-000000000031");

  @Autowired InspeccionRepository inspecciones;
  @Autowired TransicionInspeccionRepository transiciones;
  @Autowired AcompananteInspeccionRepository acompanantes;
  @Autowired EntityManager em;
  @Autowired DataSource dataSource;

  private JdbcClient jdbc;

  @BeforeEach
  void clienteSql() {
    jdbc = JdbcClient.create(dataSource);
  }

  @Test
  void lasMigracionesDeP5QuedanDespuesDeLaUltimaDeOctubre21() {
    List<String> p5 =
        jdbc.sql(
                "select version from flyway_schema_history where version like '2026.10.22.%' order by version")
            .query(String.class)
            .list();
    assertThat(p5)
        .contains(
            "2026.10.22.1000",
            "2026.10.22.1001",
            "2026.10.22.1002",
            "2026.10.22.1003",
            "2026.10.22.1004",
            "2026.10.22.1005",
            "2026.10.22.1007",
            "2026.10.22.1008")
        .doesNotContain("2026.10.22.1006");
    assertThat(p5).allMatch(version -> version.compareTo("2026.10.21.1800") > 0);
  }

  @Test
  void elContextoArrancaValidandoElEsquema() {
    assertThat(inspecciones.findById(UUID.randomUUID())).isEmpty();
  }

  @Test
  void persisteLosCamposNuevosConElIdDelCliente() {
    UUID id = UUID.fromString("0199c000-0000-7000-8000-0000000000aa");
    Instant confirmada = Instant.parse("2026-10-24T18:00:00Z");
    Instant enDispositivo = Instant.parse("2026-10-24T12:00:00Z");
    Inspeccion origen = publicada(UUID.fromString("0199c000-0000-7000-8000-0000000000ab"));
    inspecciones.save(origen);
    em.flush();

    Inspeccion inspeccion = borrador(id);
    inspeccion.setCursoId(CURSO);
    inspeccion.setPrecargadaDeId(origen.getId());
    inspeccion.setDatosPermanentesConfirmadosEn(confirmada);
    inspeccion.setCreadaEnDispositivo(enDispositivo);
    inspeccion.setUbicacionInicio(
        new GeometryFactory(new PrecisionModel(), 4326)
            .createPoint(new Coordinate(-91.5182, 14.8349)));
    inspeccion.setDistanciaAPuenteM(new BigDecimal("120.50"));

    inspecciones.save(inspeccion);
    em.flush();
    em.clear();

    Inspeccion leida = inspecciones.findById(id).orElseThrow();
    assertThat(leida.getId()).isEqualTo(id);
    assertThat(leida.getCursoId()).isEqualTo(CURSO);
    assertThat(leida.getPrecargadaDeId()).isEqualTo(origen.getId());
    assertThat(leida.getDatosPermanentesConfirmadosEn()).isEqualTo(confirmada);
    assertThat(leida.getDistanciaAPuenteM()).isEqualByComparingTo("120.50");
    assertThat(leida.getCreadaEnDispositivo()).isEqualTo(enDispositivo);
    assertThat(leida.getVersion()).isZero();
  }

  @Test
  void elContadorDeVersionDetectaEscriturasSimultaneas() {
    UUID id = UUID.fromString("0199c000-0000-7000-8000-0000000000ac");
    inspecciones.save(borrador(id));
    em.flush();
    em.clear();

    Inspeccion primera = inspecciones.findById(id).orElseThrow();
    em.detach(primera);
    Inspeccion segunda = inspecciones.findById(id).orElseThrow();
    segunda.setDispositivoId("segundo");
    inspecciones.save(segunda);
    em.flush();
    em.clear();
    assertThat(inspecciones.findById(id).orElseThrow().getVersion()).isEqualTo(1L);

    primera.setDispositivoId("primero");
    assertThatThrownBy(
            () -> {
              inspecciones.save(primera);
              em.flush();
            })
        .isInstanceOfAny(
            OptimisticLockException.class, ObjectOptimisticLockingFailureException.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("motivosDeCorreccionInvalidos")
  void laBaseRechazaUnaCorreccionSinMotivoValido(String caso, String motivo) {
    UUID original = UUID.fromString("0199c000-0000-7000-8000-0000000000b1");
    UUID correccion = UUID.fromString("0199c000-0000-7000-8000-0000000000b2");
    inspecciones.save(publicada(original));
    em.flush();
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        INSERT INTO inspeccion (id, puente_id, autor_id, formulario_version_id, fecha_inspeccion,
                          supersede_a_id, motivo_correccion)
                        VALUES (:id, :puente, :autor, :formulario, DATE '2026-10-02', :original, :motivo)
                        """)
                    .param("id", correccion)
                    .param("puente", PUENTE_A)
                    .param("autor", AUTOR)
                    .param("formulario", FORMULARIO)
                    .param("original", original)
                    .param("motivo", motivo)
                    .update())
        .as(caso)
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void laBaseRechazaUnaCorreccionDeOtroPuente() {
    UUID original = UUID.fromString("0199c000-0000-7000-8000-0000000000b3");
    UUID correccion = UUID.fromString("0199c000-0000-7000-8000-0000000000b4");
    inspecciones.save(publicada(original));
    em.flush();
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        INSERT INTO inspeccion (id, puente_id, autor_id, formulario_version_id, fecha_inspeccion,
                          supersede_a_id, motivo_correccion)
                        VALUES (:id, :puente, :autor, :formulario, DATE '2026-10-02', :original, 'error')
                        """)
                    .param("id", correccion)
                    .param("puente", PUENTE_B)
                    .param("autor", AUTOR)
                    .param("formulario", FORMULARIO)
                    .param("original", original)
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void laBaseRechazaUnaSegundaCorreccionViva() {
    UUID original = UUID.fromString("0199c000-0000-7000-8000-0000000000b5");
    inspecciones.save(publicada(original));
    Inspeccion viva = borrador(UUID.fromString("0199c000-0000-7000-8000-0000000000b6"));
    viva.setSupersedeA(inspecciones.findById(original).orElseThrow());
    viva.setMotivoCorreccion("primer error");
    inspecciones.save(viva);
    em.flush();

    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        INSERT INTO inspeccion (id, puente_id, autor_id, formulario_version_id, fecha_inspeccion,
                          supersede_a_id, motivo_correccion)
                        VALUES (:id, :puente, :autor, :formulario, DATE '2026-10-03', :original, 'otro error')
                        """)
                    .param("id", UUID.fromString("0199c000-0000-7000-8000-0000000000b7"))
                    .param("puente", PUENTE_A)
                    .param("autor", AUTOR)
                    .param("formulario", FORMULARIO)
                    .param("original", original)
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void unaCorreccionRechazadaPermiteCrearOtra() {
    UUID original = UUID.fromString("0199c000-0000-7000-8000-0000000000b8");
    inspecciones.save(publicada(original));
    Inspeccion rechazada = borrador(UUID.fromString("0199c000-0000-7000-8000-0000000000b9"));
    rechazada.setSupersedeA(inspecciones.findById(original).orElseThrow());
    rechazada.setMotivoCorreccion("mal");
    rechazada.setEstado(EstadoInspeccion.RECHAZADA);
    inspecciones.save(rechazada);
    em.flush();

    Inspeccion otra = borrador(UUID.fromString("0199c000-0000-7000-8000-0000000000ba"));
    otra.setSupersedeA(inspecciones.findById(original).orElseThrow());
    otra.setMotivoCorreccion("reintento");
    inspecciones.save(otra);
    em.flush();
    assertThat(inspecciones.findById(otra.getId())).isPresent();
  }

  @Test
  void lasRespuestasDelFormularioDebenSerUnObjetoJson() {
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        INSERT INTO inspeccion (id, puente_id, autor_id, formulario_version_id, fecha_inspeccion, datos)
                        VALUES (:id, :puente, :autor, :formulario, DATE '2026-10-01', '[]'::jsonb)
                        """)
                    .param("id", UUID.fromString("0199c000-0000-7000-8000-0000000000bb"))
                    .param("puente", PUENTE_A)
                    .param("autor", AUTOR)
                    .param("formulario", FORMULARIO)
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void unEstadoConfirmadoDistintoExigeResponsable() {
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        INSERT INTO inspeccion (id, puente_id, autor_id, formulario_version_id, fecha_inspeccion,
                          estado, indice_condicion, estado_calculado, estado_confirmado, justificacion_estado, publicada_en)
                        VALUES (:id, :puente, :autor, :formulario, DATE '2026-10-01',
                          'PUBLICADA', 80, 'BUENO', 'MALO', 'grieta', now())
                        """)
                    .param("id", UUID.fromString("0199c000-0000-7000-8000-0000000000bc"))
                    .param("puente", PUENTE_A)
                    .param("autor", AUTOR)
                    .param("formulario", FORMULARIO)
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @ParameterizedTest
  @MethodSource("transiciones")
  void elHistorialSoloAdmiteParesValidos(String origen, String destino, boolean aceptada) {
    UUID inspeccionId = UUID.fromString("0199c000-0000-7000-8000-0000000000c1");
    if (inspecciones.findById(inspeccionId).isEmpty()) {
      inspecciones.save(borrador(inspeccionId));
      em.flush();
    }
    var sentencia =
        jdbc.sql(
            """
            INSERT INTO transicion_inspeccion (id, inspeccion_id, estado_origen, estado_destino)
            VALUES (:id, :inspeccion, :origen, :destino)
            """);
    var params =
        sentencia
            .param("id", UUID.randomUUID())
            .param("inspeccion", inspeccionId)
            .param("destino", destino);
    if (origen == null) {
      params = params.param("origen", (String) null);
    } else {
      params = params.param("origen", origen);
    }
    if (aceptada) {
      assertThat(params.update()).isEqualTo(1);
    } else {
      JdbcClient.StatementSpec fallida = params;
      assertThatThrownBy(fallida::update).isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  static Stream<Arguments> motivosDeCorreccionInvalidos() {
    return Stream.of(
        Arguments.of("sin motivo de corrección", null),
        Arguments.of("con el motivo en blanco", "   "));
  }

  static Stream<Arguments> transiciones() {
    return Stream.of(
        Arguments.of(null, "BORRADOR", true),
        Arguments.of("BORRADOR", "ENVIADA", true),
        Arguments.of("ENVIADA", "PUBLICADA", true),
        Arguments.of("EN_REVISION", "CAMBIOS_SOLICITADOS", true),
        Arguments.of("CAMBIOS_SOLICITADOS", "BORRADOR", true),
        Arguments.of(null, "ENVIADA", false),
        Arguments.of("BORRADOR", "PUBLICADA", false),
        Arguments.of("PUBLICADA", "BORRADOR", false),
        Arguments.of("RECHAZADA", "BORRADOR", false));
  }

  @Test
  void unAcompananteConCuentaNoSeRepiteYVariosSinCuentaSi() {
    Inspeccion inspeccion =
        inspecciones.save(borrador(UUID.fromString("0199c000-0000-7000-8000-0000000000d1")));
    em.flush();
    acompanantes.save(acompanante(inspeccion, "Ana", null));
    acompanantes.save(acompanante(inspeccion, "Luis", null));
    acompanantes.save(acompanante(inspeccion, "Par", ACOMPANANTE));
    em.flush();
    assertThat(acompanantes.findByInspeccion_Id(inspeccion.getId())).hasSize(3);

    assertThatThrownBy(
            () -> {
              acompanantes.save(acompanante(inspeccion, "Par otra vez", ACOMPANANTE));
              em.flush();
            })
        .isInstanceOfAny(DataIntegrityViolationException.class, ConstraintViolationException.class);
  }

  @Test
  void sePuedeInsertarHistorialYBorrarAcompanantes() {
    Inspeccion inspeccion =
        inspecciones.save(borrador(UUID.fromString("0199c000-0000-7000-8000-0000000000d2")));
    em.flush();
    TransicionInspeccion alta = new TransicionInspeccion();
    alta.setInspeccion(inspeccion);
    alta.setEstadoDestino(EstadoInspeccion.BORRADOR);
    transiciones.save(alta);
    em.flush();
    assertThat(transiciones.findByInspeccion_IdOrderByCreadoEnAsc(inspeccion.getId())).hasSize(1);

    acompanantes.save(acompanante(inspeccion, "Externo", null));
    em.flush();
    acompanantes.deleteByInspeccion_Id(inspeccion.getId());
    em.flush();
    assertThat(acompanantes.findByInspeccion_Id(inspeccion.getId())).isEmpty();
  }

  private Inspeccion borrador(UUID id) {
    Inspeccion inspeccion = new Inspeccion();
    inspeccion.setId(id);
    inspeccion.setPuenteId(PUENTE_A);
    inspeccion.setAutorId(AUTOR);
    inspeccion.setFormularioVersionId(FORMULARIO);
    inspeccion.setFechaInspeccion(LocalDate.of(2026, 10, 1));
    inspeccion.setDatos(Map.of("identificacion", Map.of("tipologia", "viga")));
    return inspeccion;
  }

  private Inspeccion publicada(UUID id) {
    Inspeccion inspeccion = borrador(id);
    inspeccion.setEstado(EstadoInspeccion.PUBLICADA);
    inspeccion.setIndiceCondicion(new BigDecimal("80.00"));
    inspeccion.setEstadoCalculado(EstadoCondicion.BUENO);
    inspeccion.setEstadoConfirmado(EstadoCondicion.BUENO);
    inspeccion.setPublicadaEn(Instant.parse("2026-10-01T18:00:00Z"));
    return inspeccion;
  }

  private static AcompananteInspeccion acompanante(
      Inspeccion inspeccion, String nombre, UUID usuario) {
    AcompananteInspeccion fila = new AcompananteInspeccion();
    fila.setInspeccion(inspeccion);
    fila.setNombre(nombre);
    fila.setUsuarioId(usuario);
    return fila;
  }
}
