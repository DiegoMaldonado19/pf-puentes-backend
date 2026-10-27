package com.cunoc.puentes.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

  private final OpenApiConfig configuracion = new OpenApiConfig();

  @Test
  void declaraBearerJwtGlobalConServidorRelativo() {
    SecurityScheme esquema = OpenApiConfig.class.getAnnotation(SecurityScheme.class);
    OpenAPIDefinition api = OpenApiConfig.class.getAnnotation(OpenAPIDefinition.class);

    assertThat(esquema.name()).isEqualTo("bearerAuth");
    assertThat(esquema.type()).isEqualTo(SecuritySchemeType.HTTP);
    assertThat(esquema.scheme()).isEqualTo("bearer");
    assertThat(esquema.bearerFormat()).isEqualTo("JWT");
    assertThat(api.security())
        .singleElement()
        .satisfies(s -> assertThat(s.name()).isEqualTo("bearerAuth"));
    assertThat(api.servers()).singleElement().satisfies(s -> assertThat(s.url()).isEqualTo("/"));
  }

  @Test
  void loginPublicoAnulaElRequisitoGlobalSinAbrirOtrosMetodos() {
    PathItem login = new PathItem().get(new Operation()).post(new Operation());
    OpenAPI api = new OpenAPI().paths(new Paths().addPathItem("/api/v1/auth/login", login));

    configuracion.documentarRutasPublicas(List.of("POST /api/v1/auth/login")).customise(api);

    assertThat(login.getPost().getSecurity()).isEmpty();
    assertThat(login.getGet().getSecurity()).isNull();
  }

  @Test
  void lecturaPublicaDelForoNoEximeLaCreacionAutenticada() {
    PathItem hilos = new PathItem().get(new Operation()).post(new Operation());
    OpenAPI api =
        new OpenAPI().paths(new Paths().addPathItem("/api/v1/puentes/{puenteId}/hilos", hilos));

    configuracion.documentarRutasPublicas(List.of("GET /api/v1/puentes/*/hilos")).customise(api);

    assertThat(hilos.getGet().getSecurity()).isEmpty();
    assertThat(hilos.getPost().getSecurity()).isNull();
  }

  @Test
  void patronSinMetodoPermiteDocumentarTodosLosMetodosDeLaRutaPublica() {
    PathItem abierta = new PathItem().get(new Operation()).post(new Operation());
    OpenAPI api = new OpenAPI().paths(new Paths().addPathItem("/publica/recurso/{id}", abierta));

    configuracion.documentarRutasPublicas(List.of(" /publica/** ")).customise(api);

    assertThat(abierta.getGet().getSecurity()).isEmpty();
    assertThat(abierta.getPost().getSecurity()).isEmpty();
  }

  @Test
  void rutasPrivadasYSufijosDistintosConservanBearerGlobal() {
    PathItem notificaciones = new PathItem().get(new Operation());
    PathItem privada = new PathItem().post(new Operation());
    OpenAPI api =
        new OpenAPI()
            .paths(
                new Paths()
                    .addPathItem("/api/v1/notificaciones", notificaciones)
                    .addPathItem("/api/v1/auth/login-extra", privada));

    configuracion.documentarRutasPublicas(List.of("POST /api/v1/auth/login")).customise(api);

    assertThat(notificaciones.getGet().getSecurity()).isNull();
    assertThat(privada.getPost().getSecurity()).isNull();
  }

  @Test
  void unaEstrellaNoIncluyeSubrutasDeOtraProfundidad() {
    PathItem detalle = new PathItem().get(new Operation());
    PathItem comentarios = new PathItem().get(new Operation());
    OpenAPI api =
        new OpenAPI()
            .paths(
                new Paths()
                    .addPathItem("/api/v1/hilos/{id}", detalle)
                    .addPathItem("/api/v1/hilos/{id}/comentarios", comentarios));

    configuracion.documentarRutasPublicas(List.of("GET /api/v1/hilos/*")).customise(api);

    assertThat(detalle.getGet().getSecurity()).isEmpty();
    assertThat(comentarios.getGet().getSecurity()).isNull();
  }

  @Test
  void aceptaEspaciosYMetodoSinDistinguirMayusculas() {
    PathItem registro = new PathItem().post(new Operation());
    OpenAPI api = new OpenAPI().paths(new Paths().addPathItem("/api/v1/auth/registro", registro));

    configuracion
        .documentarRutasPublicas(List.of("", "   ", "  post   /api/v1/auth/registro  "))
        .customise(api);

    assertThat(registro.getPost().getSecurity()).isEmpty();
  }

  @Test
  void sinExcepcionesNiPathsNoSeIntroducenOperacionesPublicas() {
    OpenAPI vacia = new OpenAPI();
    configuracion.documentarRutasPublicas(List.of()).customise(vacia);
    assertThat(vacia.getPaths()).isNull();

    PathItem privada = new PathItem().get(new Operation());
    OpenAPI api = new OpenAPI().paths(new Paths().addPathItem("/privada", privada));
    configuracion.documentarRutasPublicas(List.of()).customise(api);
    assertThat(privada.getGet().getSecurity()).isNull();
  }
}
