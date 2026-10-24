package com.cunoc.puentes.inspeccion.puerto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.common.NegocioException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CalculoIndiceProvisionalTest {

  private final CalculoIndice adaptador = new CalculoIndiceProvisional();
  private final UUID version = UUID.fromString("0199b000-0000-7000-8000-00000000f001");

  @Test
  void noInventaUnIndiceDeCondicion() {
    assertThatThrownBy(() -> adaptador.calcular(Map.of(), version))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            error -> {
              NegocioException negocio = (NegocioException) error;
              assertThat(negocio.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(negocio.getBody().getType().toString()).endsWith("/ic-no-calculable");
            });
  }

  @Test
  void exigeDatosYVersion() {
    assertThatThrownBy(() -> adaptador.calcular(null, version))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adaptador.calcular(Map.of(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
