package com.cunoc.puentes.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.PruebaIntegracion;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Con un servidor real, Tomcat resuelve {@code X-Forwarded-For} (server.forward-headers-strategy):
 * la bitácora guarda la IP del cliente y no la del proxy.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "sgp.seguridad.rutas-publicas=POST /api/v1/prueba/auditoria/**")
@Import({AuditablesDePrueba.class, AuditoriaPorAspectoTest.ControladorDeAuditoria.class})
class AuditoriaConProxyTest extends PruebaIntegracion {

  @LocalServerPort int puerto;
  @Autowired JdbcTemplate jdbc;
  @Autowired UsuarioRepository usuarios;

  private UUID cuenta;

  @AfterEach
  void limpiar() {
    jdbc.update("DELETE FROM auditoria WHERE entidad_id = ?", cuenta);
    jdbc.update("DELETE FROM usuario WHERE id = ?", cuenta);
  }

  @Test
  void guardaLaIpRealDelClienteQueReenviaElProxy() throws Exception {
    cuenta =
        usuarios
            .save(Cuentas.estudiante("auditoria-proxy-" + UUID.randomUUID() + "@correo.com"))
            .getId();

    var respuesta =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                        URI.create(
                            "http://localhost:%d/api/v1/prueba/auditoria/%s/renombrar?nombre=Ana"
                                .formatted(puerto, cuenta)))
                    .header("X-Forwarded-For", "203.0.113.9")
                    .POST(BodyPublishers.noBody())
                    .build(),
                HttpResponse.BodyHandlers.discarding());

    assertThat(respuesta.statusCode()).isEqualTo(204);
    assertThat(
            jdbc.queryForObject(
                "SELECT origen_ip FROM auditoria WHERE entidad_id = ?", String.class, cuenta))
        .isEqualTo("203.0.113.9");
  }
}
