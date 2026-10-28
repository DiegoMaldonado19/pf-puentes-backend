package com.cunoc.puentes.revision;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

class DecisionRevisionDTOTest {

  @Test
  void openApiExigeObservacionParaTodoVeredicto() {
    Schema<?> esquema =
        ModelConverters.getInstance().read(DecisionRevisionDTO.class).get("DecisionRevisionDTO");

    assertThat(esquema.getRequired()).contains("veredicto", "observacion");
    var observacion = esquema.getProperties().get("observacion");
    assertThat(observacion.getPattern()).isEqualTo("[\\s\\S]*\\S[\\s\\S]*");
    var patron = java.util.regex.Pattern.compile(observacion.getPattern());
    assertThat(patron.matcher("").matches()).isFalse();
    assertThat(patron.matcher(" \t\r\n ").matches()).isFalse();
    assertThat(patron.matcher("\nConforme\n").matches()).isTrue();
    assertThat(observacion.getMaxLength()).isEqualTo(10000);
    assertThat(observacion.getDescription()).contains("APROBADA", "422");
  }
}
