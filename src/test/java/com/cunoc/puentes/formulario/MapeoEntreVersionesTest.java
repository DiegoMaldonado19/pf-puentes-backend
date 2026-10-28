package com.cunoc.puentes.formulario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** RN-FRM-06: la precarga lleva los datos de una inspección vieja a la versión activa. */
@ExtendWith(MockitoExtension.class)
class MapeoEntreVersionesTest {

  private static final UUID V1 = UUID.fromString("0192b000-0000-7000-8000-0000000000a1");
  private static final UUID V2 = UUID.fromString("0192b000-0000-7000-8000-0000000000a2");
  private static final UUID V3 = UUID.fromString("0192b000-0000-7000-8000-0000000000a3");

  @Mock FormularioVersionRepository versiones;
  @Mock FormularioValidacionService validacion;

  @Test
  void unRenombradoMueveElValorALaRutaNueva() {
    var datos = Map.<String, Object>of("subestructura", Map.of("viga_1", "Bueno", "otro", 2));

    var traducidos =
        ConsultaFormularioEsquema.aplicarMapeo(
            datos, Map.of("subestructura.viga_1", "subestructura.viga_entrada"));

    assertThat(traducidos)
        .isEqualTo(Map.of("subestructura", Map.of("viga_entrada", "Bueno", "otro", 2)));
    assertThat(datos).containsEntry("subestructura", Map.of("viga_1", "Bueno", "otro", 2));
  }

  @Test
  void unIntercambioNoPisaValores() {
    var traducidos =
        ConsultaFormularioEsquema.aplicarMapeo(
            Map.of("s", Map.of("a", 1, "b", 2)), Map.of("s.a", "s.b", "s.b", "s.a"));

    assertThat(traducidos).isEqualTo(Map.of("s", Map.of("a", 2, "b", 1)));
  }

  @Test
  void unDestinoNuloEliminaElCampo() {
    var mapeo = new HashMap<String, Object>();
    mapeo.put("s.retirado", null);

    var traducidos =
        ConsultaFormularioEsquema.aplicarMapeo(Map.of("s", Map.of("retirado", 1, "b", 2)), mapeo);

    assertThat(traducidos).isEqualTo(Map.of("s", Map.of("b", 2)));
  }

  @Test
  void aplicaEnOrdenLosMapeosPublicadosDespuesDeLaVersionDeOrigen() {
    when(versiones.findById(V1)).thenReturn(Optional.of(publicada("V1", null, 1)));
    when(versiones.findById(V3)).thenReturn(Optional.of(publicada("V3", null, 3)));
    when(versiones.findByPublicadaEnGreaterThanAndPublicadaEnLessThanEqualOrderByPublicadaEnAsc(
            Instant.ofEpochSecond(1), Instant.ofEpochSecond(3)))
        .thenReturn(
            List.of(
                publicada("V2", Map.of("s.a", "s.b"), 2),
                publicada("V3", Map.of("s.b", "s.c"), 3)));
    var consulta = new ConsultaFormularioEsquema(versiones, validacion);

    assertThat(consulta.traducir(Map.of("s", Map.of("a", 7)), V1, V3))
        .isEqualTo(Map.of("s", Map.of("c", 7)));
  }

  @Test
  void laMismaVersionNoSeTraduce() {
    when(versiones.findById(V2)).thenReturn(Optional.of(publicada("V2", null, 2)));
    var datos = Map.<String, Object>of("s", Map.of("a", 1));

    assertThat(new ConsultaFormularioEsquema(versiones, validacion).traducir(datos, V2, V2))
        .isSameAs(datos);
  }

  private static FormularioVersion publicada(
      String codigo, Map<String, Object> mapeo, long segundo) {
    var version = new FormularioVersion(codigo, Map.of("type", "object"));
    version.publicar(mapeo);
    version.setPublicadaEn(Instant.ofEpochSecond(segundo));
    return version;
  }
}
