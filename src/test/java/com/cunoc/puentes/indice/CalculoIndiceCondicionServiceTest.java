package com.cunoc.puentes.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.inspeccion.puerto.CalculoIndice;
import com.cunoc.puentes.inspeccion.puerto.ConsultaFormulario;
import com.cunoc.puentes.inspeccion.puerto.RutasDatos;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

@ExtendWith(MockitoExtension.class)
class CalculoIndiceCondicionServiceTest {

  private static final UUID FORMULARIO = UUID.fromString("019a0024-0000-7000-8000-000000000001");
  private static final UUID JUEGO = UUID.fromString("019a0024-0000-7000-8000-000000000002");
  private static final UUID JUEGO_EXPLICITO =
      UUID.fromString("019a0024-0000-7000-8000-000000000003");
  private static final CondicionesCriticas SIN_CRITICAS =
      new CondicionesCriticas(false, false, false, false);
  private static final Map<String, CampoDeDano> CAMPOS_DE_DANO =
      Map.of("estribo", new CampoDeDano(List.of("estribo"), false));

  @Mock PreparacionCalificacionesService preparacion;
  @Mock VersionPesosRepository versiones;
  @Mock DetectorCondicionesCriticas detector;
  @Mock MetadatosDanoFormulario metadatos;
  @Mock ConsultaFormulario formularios;
  @Mock JdbcClient jdbc;

  @Mock(answer = Answers.RETURNS_SELF)
  JdbcClient.StatementSpec sentencia;

  @Mock JdbcClient.MappedQuerySpec<UUID> consultaVigente;
  CalculoIndiceCondicionService servicio;

  @BeforeEach
  void preparar() {
    servicio =
        new CalculoIndiceCondicionService(
            preparacion, versiones, detector, metadatos, jdbc, formularios);
    lenient().when(jdbc.sql(anyString())).thenReturn(sentencia);
  }

  @Test
  void rechazaDatosOVersionNulosEnElCalculoDelFormulario() {
    assertThatIllegalArgumentException().isThrownBy(() -> servicio.calcular(null, FORMULARIO));
    assertThatIllegalArgumentException().isThrownBy(() -> servicio.calcular(Map.of(), null));
  }

  @Test
  void rechazaArgumentosNulosEnElCalculoConJuegoExplicito() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> servicio.calcular(null, JUEGO, Map.of(), null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> servicio.calcular(FORMULARIO, JUEGO, null, null));
  }

  @Test
  void usaElCalculoOficialCuandoElEsquemaDeclaraDano() {
    Map<String, Object> datos = Map.of("estribo", 0);
    when(metadatos.campos(FORMULARIO)).thenReturn(CAMPOS_DE_DANO);
    juegoMasReciente(JUEGO);
    calificacionesSanas(JUEGO, datos);

    CalculoIndice.ResultadoCalculo resultado = servicio.calcular(datos, FORMULARIO);

    assertThat(resultado.indiceCondicion()).isEqualTo(new BigDecimal("100.00"));
    assertThat(resultado.estadoCalculado()).isEqualTo(EstadoCondicion.BUENO);
    assertThat(resultado.anulacion()).isNull();
    assertThat(resultado.versionPesosId()).isEqualTo(JUEGO);
  }

  @Test
  void traduceElFalloDelCalculoOficialAIcNoCalculable() {
    when(metadatos.campos(FORMULARIO)).thenReturn(CAMPOS_DE_DANO);
    when(versiones.findByFormularioVersionId(eq(FORMULARIO), any(Pageable.class)))
        .thenReturn(Page.empty());

    assertThatThrownBy(() -> servicio.calcular(Map.of(), FORMULARIO))
        .isInstanceOfSatisfying(
            NegocioException.class,
            error -> {
              assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(error.getBody().getType().toString()).endsWith("/ic-no-calculable");
              assertThat(error.getBody().getTitle())
                  .isEqualTo("No se puede calcular el índice de condición");
            });
  }

  @Test
  void propagaSinCambiosLosErroresDelCalculoOficialQueNoSonSinCalculo() {
    NegocioException error =
        new NegocioException(
            HttpStatus.NOT_FOUND,
            "juego-pesos-no-encontrado",
            "Juego de pesos no encontrado",
            "El juego de pesos indicado no existe.");
    Map<String, Object> datos = Map.of("estribo", 0);
    when(metadatos.campos(FORMULARIO)).thenReturn(CAMPOS_DE_DANO);
    juegoMasReciente(JUEGO);
    when(preparacion.preparar(FORMULARIO, JUEGO, datos)).thenThrow(error);

    assertThatThrownBy(() -> servicio.calcular(datos, FORMULARIO)).isSameAs(error);
  }

  @Test
  void calculaPorRutasDirectasSiLosMetadatosDeDanoNoExisten() throws SQLException {
    when(metadatos.campos(FORMULARIO))
        .thenThrow(
            new NegocioException(
                HttpStatus.NOT_FOUND,
                "formulario-no-encontrado",
                "Formulario no encontrado",
                "La versión del formulario indicada no existe."));
    juegoDeDosElementos();

    CalculoIndice.ResultadoCalculo resultado =
        servicio.calcular(Map.of("estribo", 0, "pilas", 2), FORMULARIO);

    assertThat(resultado.indiceCondicion()).isEqualTo(new BigDecimal("75.00"));
    assertThat(resultado.estadoCalculado()).isEqualTo(EstadoCondicion.BUENO);
    assertThat(resultado.versionPesosId()).isEqualTo(JUEGO);
    verifyNoInteractions(preparacion);
  }

  @Test
  void aceptaCalificacionDirectaYCalificacionAnidadaEnLasRutas() throws SQLException {
    Map<String, Object> datos = Map.of("estribo", Map.of("calificacion", 0), "pilas", 2);
    juegoDeDosElementos();

    CalculoIndice.ResultadoCalculo resultado = servicio.calcular(datos, FORMULARIO);

    assertThat(resultado.indiceCondicion()).isEqualTo(new BigDecimal("75.00"));
  }

  @Test
  void rechazaUnaCalificacionNoEnteraSinRespaldoDeCalificacion() throws SQLException {
    juegoDeDosElementos();

    assertThatThrownBy(() -> servicio.calcular(Map.of("estribo", 0, "pilas", 2.5), FORMULARIO))
        .isInstanceOfSatisfying(
            NegocioException.class,
            error ->
                assertThat(error.getBody().getDetail())
                    .isEqualTo("Falta la calificación del elemento 'pilas' para calcular el IC."));
  }

  @Test
  void rechazaUnaVersionSinJuegoVigente() {
    hayJuegoVigente(Optional.empty());

    assertThatThrownBy(() -> servicio.calcular(Map.of(), FORMULARIO))
        .isInstanceOfSatisfying(
            NegocioException.class,
            error ->
                assertThat(error.getBody().getDetail())
                    .isEqualTo(
                        "No hay un juego de pesos vigente para esa versión del formulario."));
  }

  @Test
  void rechazaUnJuegoVigenteSinElementos() throws SQLException {
    hayJuegoVigente(Optional.of(JUEGO));
    pesosDelJuego();

    assertThatThrownBy(() -> servicio.calcular(Map.of(), FORMULARIO))
        .isInstanceOfSatisfying(
            NegocioException.class,
            error ->
                assertThat(error.getBody().getDetail())
                    .isEqualTo("El juego de pesos vigente no tiene elementos."));
  }

  @ParameterizedTest
  @MethodSource("rutasDeDano")
  void detectaLaAnulacionPorRutaDeDanoYSuValor(String ruta, Object valor, Anulacion esperada)
      throws SQLException {
    Map<String, Object> datos = new HashMap<>(Map.of("estribo", 0));
    RutasDatos.escribir(datos, ruta, valor);
    when(formularios.rutasDeDano(FORMULARIO)).thenReturn(Set.of(ruta));
    hayJuegoVigente(Optional.of(JUEGO));
    pesosDelJuego(new FilaPeso("estribo", BigDecimal.ONE));

    CalculoIndice.ResultadoCalculo resultado = servicio.calcular(datos, FORMULARIO);

    assertThat(resultado.anulacion()).isEqualTo(esperada);
  }

  static Stream<Arguments> rutasDeDano() {
    return Stream.of(
        arguments("subestructura.estribo_entrada.socavacion", true, Anulacion.SOCAVACION),
        arguments("subestructura.cimentacion.asentamiento", 1, Anulacion.ASENTAMIENTO),
        arguments("superestructura.vigas.perdida_seccion", 2, Anulacion.PERDIDA_SECCION),
        arguments("superestructura.vigas.portante_cortado", 1, Anulacion.PERDIDA_SECCION),
        arguments("superestructura.vigas.ausencia_portante", 1, Anulacion.AUSENCIA_PORTANTE),
        arguments("socavacion", true, Anulacion.SOCAVACION),
        arguments("superestructura.vigas.ausencia_portante", 0, null),
        arguments("superestructura.vigas.ausencia_portante", false, null),
        arguments("superestructura.vigas.ausencia_portante", "1", null),
        arguments("superestructura.vigas.ausencia_portante", 1.5, null),
        arguments("superestructura.vigas.otro", 2, null));
  }

  @Test
  void leeLasCondicionesExplicitasDeLosDatos() throws SQLException {
    Map<String, Object> datos =
        Map.of(
            "estribo",
            0,
            "condiciones",
            Map.of(
                "socavacion_cimentacion_expuesta", true,
                "asentamiento_estructura", 1,
                "portante_perdida_seccion", 1,
                "ausencia_portante", 1));
    hayJuegoVigente(Optional.of(JUEGO));
    pesosDelJuego(new FilaPeso("estribo", BigDecimal.ONE));

    CalculoIndice.ResultadoCalculo resultado = servicio.calcular(datos, FORMULARIO);

    assertThat(resultado.anulacion()).isEqualTo(Anulacion.SOCAVACION);
    assertThat(resultado.estadoCalculado()).isEqualTo(EstadoCondicion.MALO);
  }

  @Test
  void calculaElIndiceConElJuegoMasRecienteDeLaVersion() {
    Map<String, Object> datos = Map.of("estribo", 0);
    juegoMasReciente(JUEGO);
    calificacionesSanas(JUEGO, datos);

    ResultadoCalculoOficialIc resultado = servicio.calcular(FORMULARIO, datos);

    assertThat(resultado.versionPesosId()).isEqualTo(JUEGO);
    assertThat(resultado.indiceCondicion()).isEqualTo(new BigDecimal("100.00"));
    assertThat(resultado.anulaciones()).isEmpty();
  }

  @Test
  void usaElJuegoExplicitoSinConsultarElVigente() {
    Map<String, Object> datos = Map.of("estribo", 0);
    calificacionesSanas(JUEGO_EXPLICITO, datos);

    ResultadoCalculoOficialIc resultado =
        servicio.calcular(FORMULARIO, JUEGO_EXPLICITO, datos, null);

    assertThat(resultado.versionPesosId()).isEqualTo(JUEGO_EXPLICITO);
    verifyNoInteractions(versiones);
  }

  @Test
  void rechazaUnFormularioSinJuegosDePesos() {
    when(versiones.findByFormularioVersionId(eq(FORMULARIO), any(Pageable.class)))
        .thenReturn(Page.empty());

    assertThatThrownBy(() -> servicio.calcular(FORMULARIO, null, Map.of(), null))
        .isInstanceOfSatisfying(
            NegocioException.class,
            error -> {
              assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(error.getBody().getTitle()).isEqualTo("Sin juegos de pesos configurados");
            });
  }

  @Test
  void combinaLasCondicionesDetectadasConLasExplicitas() {
    Map<String, Object> datos = Map.of("estribo", 0);
    juegoMasReciente(JUEGO);
    when(preparacion.preparar(FORMULARIO, JUEGO, datos))
        .thenReturn(List.of(new CalificacionElemento(0, BigDecimal.ONE)));
    when(detector.detectar(FORMULARIO, datos))
        .thenReturn(new CondicionesCriticas(true, false, false, false));

    ResultadoCalculoOficialIc resultado =
        servicio.calcular(
            FORMULARIO, null, datos, new CondicionesCriticas(false, true, false, false));

    assertThat(resultado.anulaciones())
        .containsExactlyInAnyOrder(Anulacion.SOCAVACION, Anulacion.ASENTAMIENTO);
    assertThat(resultado.anulacionPrincipal()).isEqualTo(Anulacion.SOCAVACION);
    assertThat(resultado.estadoCalculado()).isEqualTo(EstadoCondicion.MALO);
  }

  @ParameterizedTest
  @CsvSource({
    "true,true,true,true,SOCAVACION",
    "false,true,true,true,ASENTAMIENTO",
    "false,false,true,true,PERDIDA_SECCION",
    "false,false,false,true,AUSENCIA_PORTANTE"
  })
  void eligeComoPrincipalLaPrimeraCausaEnElOrdenDelCatalogo(
      boolean socavacion,
      boolean asentamiento,
      boolean perdida,
      boolean ausencia,
      Anulacion esperada) {
    Map<String, Object> datos = Map.of("estribo", 0);
    juegoMasReciente(JUEGO);
    calificacionesSanas(JUEGO, datos);

    ResultadoCalculoOficialIc resultado =
        servicio.calcular(
            FORMULARIO,
            null,
            datos,
            new CondicionesCriticas(socavacion, asentamiento, perdida, ausencia));

    assertThat(resultado.anulacionPrincipal()).isEqualTo(esperada);
  }

  @Test
  void sinCondicionesCriticasNoHayAnulacionPrincipalYElEstadoSigueElIndice() {
    Map<String, Object> datos = Map.of("estribo", 0);
    juegoMasReciente(JUEGO);
    calificacionesSanas(JUEGO, datos);

    ResultadoCalculoOficialIc resultado = servicio.calcular(FORMULARIO, null, datos, null);

    assertThat(resultado.anulacionPrincipal()).isNull();
    assertThat(resultado.estadoCalculado()).isEqualTo(EstadoCondicion.BUENO);
  }

  @Test
  void redondeaElIndiceADosDecimalesConMitadHaciaArriba() {
    Map<String, Object> datos = Map.of("estribo", 1, "pilas", 0);
    juegoMasReciente(JUEGO);
    when(preparacion.preparar(FORMULARIO, JUEGO, datos))
        .thenReturn(
            List.of(
                new CalificacionElemento(1, BigDecimal.ONE),
                new CalificacionElemento(0, new BigDecimal("2"))));
    when(detector.detectar(FORMULARIO, datos)).thenReturn(SIN_CRITICAS);

    ResultadoCalculoOficialIc resultado = servicio.calcular(FORMULARIO, null, datos, null);

    assertThat(resultado.indiceCondicion()).isEqualTo(new BigDecimal("91.67"));
    assertThat(resultado.estadoCalculado()).isEqualTo(EstadoCondicion.BUENO);
  }

  private void calificacionesSanas(UUID juego, Map<String, Object> datos) {
    when(preparacion.preparar(FORMULARIO, juego, datos))
        .thenReturn(List.of(new CalificacionElemento(0, BigDecimal.ONE)));
    when(detector.detectar(FORMULARIO, datos)).thenReturn(SIN_CRITICAS);
  }

  private void juegoDeDosElementos() throws SQLException {
    hayJuegoVigente(Optional.of(JUEGO));
    pesosDelJuego(new FilaPeso("estribo", BigDecimal.ONE), new FilaPeso("pilas", BigDecimal.ONE));
  }

  private void hayJuegoVigente(Optional<UUID> juego) {
    when(sentencia.query(UUID.class)).thenReturn(consultaVigente);
    when(consultaVigente.optional()).thenReturn(juego);
  }

  private void juegoMasReciente(UUID id) {
    VersionPesos version = mock(VersionPesos.class);
    when(version.getId()).thenReturn(id);
    when(versiones.findByFormularioVersionId(eq(FORMULARIO), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(version)));
  }

  private void pesosDelJuego(FilaPeso... filas) throws SQLException {
    List<ResultSet> resultados = new ArrayList<>();
    for (FilaPeso fila : filas) {
      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("elemento_ref")).thenReturn(fila.referencia());
      when(rs.getBigDecimal("peso")).thenReturn(fila.peso());
      resultados.add(rs);
    }
    when(sentencia.query(any(RowMapper.class)))
        .thenAnswer(invocacion -> mapear(invocacion.getArgument(0), resultados));
  }

  private static JdbcClient.MappedQuerySpec<Object> mapear(
      RowMapper<?> mapeador, List<ResultSet> filas) throws SQLException {
    List<Object> mapeadas = new ArrayList<>();
    for (ResultSet fila : filas) {
      mapeadas.add(mapeador.mapRow(fila, 0));
    }
    JdbcClient.MappedQuerySpec<Object> consulta = mock(JdbcClient.MappedQuerySpec.class);
    when(consulta.list()).thenReturn(mapeadas);
    return consulta;
  }

  private record FilaPeso(String referencia, BigDecimal peso) {}
}
