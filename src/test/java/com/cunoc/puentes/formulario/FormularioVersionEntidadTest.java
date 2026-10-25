package com.cunoc.puentes.formulario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FormularioVersionEntidadTest {

  @Test
  @DisplayName("Constructor inicializa en estado BORRADOR sin fecha de publicación")
  void constructor_inicializaEnBorrador() {
    Map<String, Object> esquema = Map.of("type", "object");
    FormularioVersion version = new FormularioVersion("V1.0.0", esquema);

    assertThat(version.getCodigo()).isEqualTo("V1.0.0");
    assertThat(version.getEsquema()).isEqualTo(esquema);
    assertThat(version.getEstado()).isEqualTo(EstadoFormulario.BORRADOR);
    assertThat(version.getPublicadaEn()).isNull();
    assertThat(version.getMapeo()).isNull();
  }

  @Test
  @DisplayName("Transición publicar() pasa a estado ACTIVA, fija fecha y guarda mapeo")
  void publicar_cambiaEstadoAActiva() {
    FormularioVersion version = new FormularioVersion("V1.0.0", Map.of("type", "object"));
    Map<String, Object> mapeo = Map.of("campo_viejo", "campo_nuevo");

    version.publicar(mapeo);

    assertThat(version.getEstado()).isEqualTo(EstadoFormulario.ACTIVA);
    assertThat(version.getPublicadaEn()).isNotNull();
    assertThat(version.getMapeo()).isEqualTo(mapeo);
  }

  @Test
  @DisplayName("Transición retirar() pasa a estado RETIRADA")
  void retirar_cambiaEstadoARetirada() {
    FormularioVersion version = new FormularioVersion("V1.0.0", Map.of("type", "object"));
    version.publicar(null);

    version.retirar();

    assertThat(version.getEstado()).isEqualTo(EstadoFormulario.RETIRADA);
  }

  @Test
  @DisplayName("Enum EstadoFormulario contiene los tres estados requeridos")
  void estadoFormulario_valoresCorrectos() {
    assertThat(EstadoFormulario.valueOf("BORRADOR")).isEqualTo(EstadoFormulario.BORRADOR);
    assertThat(EstadoFormulario.valueOf("ACTIVA")).isEqualTo(EstadoFormulario.ACTIVA);
    assertThat(EstadoFormulario.valueOf("RETIRADA")).isEqualTo(EstadoFormulario.RETIRADA);
  }
}
