package com.cunoc.puentes.common.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Recorre los endpoints que publica Spring: sin sesión, todo lo que no esté en {@code
 * sgp.seguridad.rutas-publicas} responde 401, y cada endpoint privado declara sus roles con
 * {@code @PreAuthorize}. Un endpoint nuevo queda cubierto sin tocar esta prueba.
 */
@SpringBootTest(properties = "sgp.correo.modo=smtp")
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class AutorizacionPorEndpointTest extends PruebaIntegracion {

  private static final String ID = "0192f5a0-0000-7000-8000-000000000099";

  @Autowired MockMvc mvc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mapeos;

  @Value("${sgp.seguridad.rutas-publicas}")
  List<String> rutasPublicas;

  record Endpoint(HttpMethod metodo, String ruta, HandlerMethod handler) {
    @Override
    public String toString() {
      return metodo + " " + ruta;
    }
  }

  @TestFactory
  Stream<DynamicTest> sinSesionCadaEndpointPrivadoResponde401() {
    return privados()
        .map(
            endpoint ->
                dynamicTest(
                    endpoint.toString(),
                    () ->
                        mvc.perform(request(endpoint.metodo(), endpoint.ruta()))
                            .andExpect(status().isUnauthorized())));
  }

  @Test
  void cadaEndpointPrivadoDeclaraSusRoles() {
    // Sin endpoints, la fábrica de arriba pasaría sin probar nada
    assertThat(privados()).hasSizeGreaterThan(50);
    assertThat(
            privados().filter(endpoint -> !declaraRoles(endpoint.handler())).map(Object::toString))
        .isEmpty();
  }

  private Stream<Endpoint> privados() {
    return endpoints().filter(endpoint -> !esPublico(endpoint));
  }

  private Stream<Endpoint> endpoints() {
    return mapeos.getHandlerMethods().entrySet().stream()
        .filter(e -> e.getValue().getBeanType().getPackageName().startsWith("com.cunoc.puentes"))
        .flatMap(
            e -> {
              var metodos = e.getKey().getMethodsCondition().getMethods();
              return e.getKey().getPatternValues().stream()
                  .flatMap(
                      patron ->
                          (metodos.isEmpty()
                                  ? Stream.of(HttpMethod.GET)
                                  : metodos.stream().map(m -> HttpMethod.valueOf(m.name())))
                              .map(
                                  metodo ->
                                      new Endpoint(
                                          metodo,
                                          patron.replaceAll("\\{[^}]+}", ID),
                                          e.getValue())));
            });
  }

  /** Misma forma que {@code ConfiguracionSeguridad}: "/ruta" o "MÉTODO /ruta". */
  private boolean esPublico(Endpoint endpoint) {
    return rutasPublicas.stream()
        .map(declaracion -> declaracion.trim().split("\\s+", 2))
        .anyMatch(
            partes -> {
              String patron = partes[partes.length - 1];
              boolean metodo =
                  partes.length == 1 || HttpMethod.valueOf(partes[0]).equals(endpoint.metodo());
              return metodo
                  && PathPatternParser.defaultInstance
                      .parse(patron)
                      .matches(PathContainer.parsePath(endpoint.ruta()));
            });
  }

  private static boolean declaraRoles(HandlerMethod handler) {
    return AnnotatedElementUtils.hasAnnotation(handler.getMethod(), PreAuthorize.class)
        || AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), PreAuthorize.class);
  }
}
