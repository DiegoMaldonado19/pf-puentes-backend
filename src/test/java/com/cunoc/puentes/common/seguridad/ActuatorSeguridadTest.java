package com.cunoc.puentes.common.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.PruebaIntegracion;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;

/** DT-DEP-10: el healthcheck consulta el puerto de gestión sin token; el resto sigue privado. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=0")
class ActuatorSeguridadTest extends PruebaIntegracion {

  @LocalManagementPort int puerto;

  @Test
  void elHealthcheckNoNecesitaToken() throws Exception {
    assertThat(estado("/actuator/health")).isEqualTo(200);
  }

  @Test
  void lasMetricasSiguenPrivadas() throws Exception {
    assertThat(estado("/actuator/metrics")).isEqualTo(401);
  }

  private int estado(String ruta) throws Exception {
    return HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).build(),
            HttpResponse.BodyHandlers.discarding())
        .statusCode();
  }
}
