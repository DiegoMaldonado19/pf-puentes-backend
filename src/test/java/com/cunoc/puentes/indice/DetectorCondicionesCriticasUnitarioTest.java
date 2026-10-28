package com.cunoc.puentes.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cunoc.puentes.common.NegocioException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class DetectorCondicionesCriticasUnitarioTest {

  private static final UUID VERSION = UUID.fromString("0192b000-0000-7000-8000-000000000001");
  private static final String ESQUEMA =
      """
      {"type":"object","properties":{"subestructura":{"type":"object","properties":{
        "estribo":{"type":"integer","x-dano":true,"x-elemento-ref":"subestructura.estribo.socavacion","x-anulacion-critica":"socavacion_cimentacion"},
        "asentamiento":{"type":"integer","x-dano":true,"x-elemento-ref":"subestructura.asentamiento","x-anulacion-critica":"asentamiento_estructura"}
      }}}}
      """;

  @Mock JdbcTemplate jdbc;
  DetectorCondicionesCriticas detector;

  @BeforeEach
  void preparar() {
    detector = new DetectorCondicionesCriticas(jdbc);
  }

  @Test
  void rechazaArgumentosNulosAlDetectarDesdeLaVersion() {
    assertThatIllegalArgumentException().isThrownBy(() -> detector.detectar(null, Map.of()));
    assertThatIllegalArgumentException().isThrownBy(() -> detector.detectar(VERSION, null));
  }

  @Test
  void rechazaArgumentosNulosAlBuscarReferenciasCriticas() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> detector.referenciasCriticasActivas(null, Map.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> detector.referenciasCriticasActivas(VERSION, null));
  }

  @Test
  void rechazaArgumentosNulosAlDetectarDesdeUnEsquemaEnMemoria() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> detector.detectarDesdeEsquema(null, Map.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> detector.detectarDesdeEsquema(Map.of(), null));
  }

  @Test
  void rechazaUnaVersionInexistente() {
    when(jdbc.query(anyString(), any(RowMapper.class), any())).thenReturn(List.of());

    assertThatThrownBy(() -> detector.detectar(VERSION, Map.of()))
        .isInstanceOfSatisfying(
            NegocioException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void rechazaUnEsquemaPersistidoQueNoEsUnObjetoJson() throws SQLException {
    persistirEsquema("[]");

    assertThatThrownBy(() -> detector.detectar(VERSION, Map.of()))
        .isInstanceOfSatisfying(
            NegocioException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
  }

  @Test
  void detectaLasBanderasYLasReferenciasDesdeElEsquemaPersistido() throws SQLException {
    persistirEsquema(ESQUEMA);
    Map<String, Object> datos = Map.of("subestructura", Map.of("estribo", 3, "asentamiento", 2));

    CondicionesCriticas resultado = detector.detectar(VERSION, datos);

    assertThat(resultado.socavacionConCimentacionExpuestaEnEstriboOPila()).isTrue();
    assertThat(resultado.asentamientoDeEstructura()).isFalse();
    assertThat(detector.referenciasCriticasActivas(VERSION, datos))
        .containsExactly("subestructura.estribo.socavacion");
  }

  @ParameterizedTest
  @CsvSource({
    "socavacion_cimentacion,SOCAVACION",
    "asentamiento_estructura,ASENTAMIENTO",
    "elemento_portante_cortado,PERDIDA_SECCION",
    "ausencia_total_elemento,AUSENCIA_PORTANTE"
  })
  void activaLaBanderaDelMarcadorCuandoLaCalificacionAlcanzaElUmbral(
      String marcador, Anulacion esperada) {
    Map<String, Object> esquema = objeto(Map.of("r", campo("r", marcador)));

    CondicionesCriticas resultado = detector.detectarDesdeEsquema(esquema, Map.of("r", 3));

    assertThat(resultado.anulaciones()).containsExactly(esperada);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void noActivaLaAnulacionBajoElUmbralCritico(int calificacion) {
    Map<String, Object> esquema = objeto(Map.of("r", campo("r", "socavacion_cimentacion")));

    CondicionesCriticas resultado =
        detector.detectarDesdeEsquema(esquema, Map.of("r", calificacion));

    assertThat(resultado.anulaciones()).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("camposNoRegistrables")
  void noRegistraCamposSinDanoReferenciaOMarcadorValidos(Map<String, Object> campo) {
    Map<String, Object> esquema = objeto(Map.of("r", campo));

    CondicionesCriticas resultado = detector.detectarDesdeEsquema(esquema, Map.of("r", 4));

    assertThat(resultado.anulaciones()).isEmpty();
  }

  static Stream<Map<String, Object>> camposNoRegistrables() {
    return Stream.of(
        Map.of("x-dano", true, "x-elemento-ref", 7, "x-anulacion-critica", "socavacion"),
        Map.of("x-dano", true, "x-elemento-ref", " ", "x-anulacion-critica", "socavacion"),
        Map.of("x-dano", true, "x-anulacion-critica", "socavacion"),
        Map.of("x-dano", true, "x-elemento-ref", "r"),
        Map.of("x-dano", true, "x-elemento-ref", "r", "x-anulacion-critica", " "),
        Map.of("x-dano", true, "x-elemento-ref", "r", "x-anulacion-critica", 1),
        Map.of("x-dano", true, "x-elemento-ref", "r", "x-anulacion-critica", "desconocido"),
        Map.of("x-dano", false, "x-elemento-ref", "r", "x-anulacion-critica", "socavacion"));
  }

  @Test
  void ignoraPropiedadesQueNoDescribenUnCampo() {
    Map<String, Object> esquema = objeto(Map.of("r", "texto"));

    assertThat(detector.detectarDesdeEsquema(esquema, Map.of()).anulaciones()).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("datosDeRutaProfunda")
  void leeLaCalificacionPorRutaYSoloActivaDesdeElUmbral(Map<String, Object> datos, boolean activa) {
    Map<String, Object> esquema =
        objeto(
            Map.of(
                "a",
                objeto(
                    Map.of("b", objeto(Map.of("c", campo("a.b.c", "socavacion_cimentacion")))))));

    Set<Anulacion> anulaciones = detector.detectarDesdeEsquema(esquema, datos).anulaciones();

    assertThat(anulaciones).isEqualTo(activa ? Set.of(Anulacion.SOCAVACION) : Set.of());
  }

  static Stream<Arguments> datosDeRutaProfunda() {
    return Stream.of(
        arguments(Map.of("a", Map.of("b", 4)), false),
        arguments(Map.of("a", Map.of("x", 4)), false),
        arguments(Map.of("a", Map.of("b", Map.of("c", "4"))), false),
        arguments(Map.of("a", Map.of("b", Map.of("c", 3.5))), false),
        arguments(Map.of("a", Map.of("b", Map.of("c", 2))), false),
        arguments(Map.of("a", Map.of("b", Map.of("c", 3.0))), true),
        arguments(Map.of("a", Map.of("b", Map.of("c", 4L))), true));
  }

  @ParameterizedTest
  @CsvSource({
    "socavacion_cimentacion, SOCAVACION",
    "socavacion, SOCAVACION",
    "asentamiento_estructura, ASENTAMIENTO",
    "asentamiento, ASENTAMIENTO",
    "elemento_portante_cortado, PERDIDA_SECCION",
    "perdida_seccion, PERDIDA_SECCION",
    "ausencia_total_elemento, AUSENCIA_PORTANTE",
    "ausencia_portante, AUSENCIA_PORTANTE"
  })
  void traduceCadaMarcadorDelFormulario(String marcador, Anulacion esperada) {
    assertThat(DetectorCondicionesCriticas.traducirMarcador(marcador)).isEqualTo(esperada);
  }

  @Test
  void normalizaMayusculasYEspaciosYRechazaMarcadoresDesconocidos() {
    assertThat(DetectorCondicionesCriticas.traducirMarcador("  SOCAVACION "))
        .isEqualTo(Anulacion.SOCAVACION);
    assertThat(DetectorCondicionesCriticas.traducirMarcador("otro")).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "true,false,false,false",
    "false,true,false,false",
    "false,false,true,false",
    "false,false,false,true"
  })
  void combinaCadaBanderaComoDisyuncion(
      boolean socavacion, boolean asentamiento, boolean perdida, boolean ausencia) {
    CondicionesCriticas bandera =
        new CondicionesCriticas(socavacion, asentamiento, perdida, ausencia);
    CondicionesCriticas ninguna = new CondicionesCriticas(false, false, false, false);

    assertThat(DetectorCondicionesCriticas.combinar(bandera, ninguna)).isEqualTo(bandera);
    assertThat(DetectorCondicionesCriticas.combinar(ninguna, bandera)).isEqualTo(bandera);
  }

  @Test
  void combinaConArgumentosNulos() {
    CondicionesCriticas bandera = new CondicionesCriticas(true, false, false, false);

    assertThat(DetectorCondicionesCriticas.combinar(null, null))
        .isEqualTo(new CondicionesCriticas(false, false, false, false));
    assertThat(DetectorCondicionesCriticas.combinar(bandera, null)).isEqualTo(bandera);
    assertThat(DetectorCondicionesCriticas.combinar(null, bandera)).isEqualTo(bandera);
  }

  private void persistirEsquema(String esquema) throws SQLException {
    ResultSet fila = mock(ResultSet.class);
    when(fila.getString(1)).thenReturn(esquema);
    when(jdbc.query(anyString(), any(RowMapper.class), any()))
        .thenAnswer(
            invocacion -> List.of(((RowMapper<?>) invocacion.getArgument(1)).mapRow(fila, 1)));
  }

  private static Map<String, Object> objeto(Map<String, Object> propiedades) {
    return Map.of("type", "object", "properties", propiedades);
  }

  private static Map<String, Object> campo(String referencia, String marcador) {
    return Map.of("x-dano", true, "x-elemento-ref", referencia, "x-anulacion-critica", marcador);
  }
}
