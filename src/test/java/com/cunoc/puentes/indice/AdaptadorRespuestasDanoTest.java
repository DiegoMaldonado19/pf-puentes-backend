package com.cunoc.puentes.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.common.NegocioException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AdaptadorRespuestasDanoTest {
  private static final UUID FORM = UUID.fromString("019a0025-0000-7000-8000-000000000001");
  private final AdaptadorRespuestasDano adaptador = new AdaptadorRespuestasDano();
  private final Map<String, CampoDeDano> campos =
      Map.of("elemento.a", new CampoDeDano(List.of("seccion", "nombre_real"), false));
  private final JuegoPesos juego = juego("elemento.a");

  @Test
  void usaRutaRealIgnoraLaReferenciaComoDatoYConservaCeroSinModificarEntradas() {
    var datos = Map.<String, Object>of("seccion", Map.of("nombre_real", 0), "elemento.a", 4);
    var resultado = adaptador.adaptar(campos, juego, datos);
    assertThat(resultado).containsExactly(new CalificacionElemento(0, BigDecimal.ONE));
    assertThat(datos).containsEntry("elemento.a", 4);
    assertThatThrownBy(() -> resultado.clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest
  @MethodSource("enteros")
  void aceptaValoresNumericamenteEnterosSinCoercionDeTexto(Number valor) {
    assertThat(adaptador.adaptar(campos, juego, datos(valor)))
        .containsExactly(new CalificacionElemento(2, BigDecimal.ONE));
  }

  static Stream<Number> enteros() {
    return Stream.of(2, 2L, (short) 2, (byte) 2, BigInteger.TWO, new BigDecimal("2.000"), 2.0);
  }

  @ParameterizedTest
  @MethodSource("invalidos")
  void rechazaAusentesNoAplicablesFraccionesYValoresFueraDeEscala(Object valor) {
    falla(campos, juego, datos(valor), "respuesta-dano-invalida");
  }

  static Stream<Object> invalidos() {
    return Stream.of(
        null,
        "2",
        "N/A",
        true,
        -1,
        5,
        new BigDecimal("1.5"),
        new BigInteger("4294967296"),
        Double.NaN,
        Double.POSITIVE_INFINITY,
        new BigDecimal("1e100"),
        List.of(2),
        Map.of("valor", 2));
  }

  @Test
  void noBuscaOtraRutaNiConsideraSanoUnCampoFaltanteOUnObjetoIncompatible() {
    for (var datos :
        List.<Map<String, Object>>of(
            Map.of(),
            Map.of("elemento.a", 0),
            Map.of("seccion", Map.of()),
            Map.of("seccion", 0),
            Map.of("seccion", List.of(Map.of("nombre_real", 2))))) {
      falla(campos, juego, datos, "respuesta-dano-invalida");
    }
  }

  @Test
  void rechazaJuegoIncompletoAjenoOConCamposEliminadosSinUsarUnSubconjunto() {
    falla(campos, juego("otro"), datos(2), "juego-pesos-incompatible");
    falla(Map.of(), juego, datos(2), "juego-pesos-incompatible");
    falla(
        Map.of("elemento.a", campos.get("elemento.a"), "nuevo", campos.get("elemento.a")),
        juego,
        datos(2),
        "juego-pesos-incompatible");
  }

  @Test
  void rechazaDanosRepetidosInclusoConArregloVacioSinElegirUnaAgregacion() {
    falla(
        Map.of("elemento.a", new CampoDeDano(List.of("tramos", "dano"), true)),
        juego,
        Map.of("tramos", List.of()),
        "dano-repetido-no-soportado");
  }

  @Test
  void preparaEnOrdenDeReferenciaYNoDeRespuestaOPesoSinCalcularEstado() {
    var dosCampos =
        Map.of(
            "a", new CampoDeDano(List.of("b.dato"), false),
            "b", new CampoDeDano(List.of("a.dato"), false));
    var dosPesos =
        new JuegoPesos(
            FORM,
            null,
            List.of(
                new PesoConfigurado("b", BigDecimal.TEN),
                new PesoConfigurado("a", BigDecimal.ONE)));
    assertThat(adaptador.adaptar(dosCampos, dosPesos, Map.of("a.dato", 4, "b.dato", 1)))
        .containsExactly(
            new CalificacionElemento(1, BigDecimal.ONE),
            new CalificacionElemento(4, BigDecimal.TEN));
  }

  @Test
  void exigeParametrosInternos() {
    assertThatThrownBy(() -> adaptador.adaptar(null, juego, datos(2)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adaptador.adaptar(campos, null, datos(2)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adaptador.adaptar(campos, juego, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static JuegoPesos juego(String ref) {
    return new JuegoPesos(FORM, null, List.of(new PesoConfigurado(ref, BigDecimal.ONE)));
  }

  private static Map<String, Object> datos(Object valor) {
    var seccion = new HashMap<String, Object>();
    seccion.put("nombre_real", valor);
    return Map.of("seccion", seccion);
  }

  private void falla(
      Map<String, CampoDeDano> campos, JuegoPesos juego, Map<String, Object> datos, String tipo) {
    assertThatThrownBy(() -> adaptador.adaptar(campos, juego, datos))
        .isInstanceOfSatisfying(
            NegocioException.class,
            e -> {
              assertThat(e.getStatusCode().value()).isEqualTo(422);
              assertThat(e.getBody().getType().getPath()).endsWith(tipo);
              assertThat(e.getBody().getProperties()).containsKey("errores");
            });
  }
}
