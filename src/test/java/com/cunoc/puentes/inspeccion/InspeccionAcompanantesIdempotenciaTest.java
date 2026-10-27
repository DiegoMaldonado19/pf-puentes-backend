package com.cunoc.puentes.inspeccion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class InspeccionAcompanantesIdempotenciaTest extends ContextoCapturaIdempotenciaHttp {
  @Override
  String sufijo() {
    return "/acompanantes";
  }

  @Override
  String cuerpo(boolean nuevo) {
    return nuevo
        ? "{\"acompanantes\":[{\"nombre\":\"Posterior\"}]}"
        : "{\"acompanantes\":[{\"nombre\":\"Par de campo\",\"usuario_id\":\""
            + InspeccionConsultaTest.E2
            + "\"},{\"nombre\":\"Externo\"}]}";
  }

  @Override
  String invalido() {
    return "{\"acompanantes\":[{\"nombre\":\"Yo\",\"usuario_id\":\"" + AUTOR + "\"}]}";
  }

  @Test
  void ordenDeListaYNombreOriginalParticipanEnHuella() throws Exception {
    MvcResult primera = operar(ID, "orden", cuerpo(false));
    exito(primera);
    List<String> antes = instantanea();
    for (String distinto :
        List.of(
            "{\"acompanantes\":[{\"nombre\":\"Externo\"},{\"nombre\":\"Par de campo\",\"usuario_id\":\""
                + InspeccionConsultaTest.E2
                + "\"}]}",
            cuerpo(false).replace("Par de campo", " Par de campo "),
            cuerpo(false)
                .replace(
                    InspeccionConsultaTest.E2.toString(), InspeccionConsultaTest.C1.toString()),
            "{\"acompanantes\":[]}")) {
      MvcResult respuesta = operar(ID, "orden", distinto);
      assertThat(respuesta.getResponse().getStatus()).isEqualTo(409);
      assertThat(respuesta.getResponse().getContentAsString())
          .contains("clave-idempotencia-incompatible");
      assertThat(instantanea()).isEqualTo(antes);
    }
  }

  @Test
  void uuidCanonicoYOrdenDePropiedadesNoRecreanRelaciones() throws Exception {
    String id = InspeccionConsultaTest.PROFESIONAL.toString();
    String original =
        "{\"acompanantes\":[{\"nombre\":\"Profesional\",\"usuario_id\":\"" + id + "\"}]}";
    MvcResult primera = operar(ID, "uuid", original);
    exito(primera);
    List<String> antes = instantanea();
    igual(
        operar(
            ID,
            "uuid",
            "{\"acompanantes\":[{\"usuario_id\":\""
                + id.toUpperCase(Locale.ROOT)
                + "\",\"nombre\":\"Profesional\"}]}"),
        primera);
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void nombreInvalidoSeRechazaAntesDeBorrarListaYNoReservaClave() throws Exception {
    List<String> antes = instantanea();
    MvcResult referencia = operar(ID, null, "{\"acompanantes\":[{\"nombre\":\" \"}]}");
    assertThat(referencia.getResponse().getStatus()).isEqualTo(400);
    igual(operar(ID, "nombre-invalido", "{\"acompanantes\":[{\"nombre\":\" \"}]}"), referencia);
    assertThat(instantanea()).isEqualTo(antes);
    exito(operar(ID, "nombre-invalido", cuerpo(false)));
    assertThat(resultados()).isEqualTo(1);
  }
}
