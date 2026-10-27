package com.cunoc.puentes.common;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

/** DT-BE-12. Servidor relativo: "Try it out" usa el origen de la página, con su puerto. */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
    info =
        @Info(
            title = "API del Sistema de Gestión de Puentes",
            version = "v1",
            description = "Los errores responden en RFC 7807 (application/problem+json)."),
    servers = @Server(url = "/"),
    security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description =
        "Inicia sesión en /api/v1/auth/login y pega únicamente el access_token, sin el prefijo Bearer.")
class OpenApiConfig {

  @Bean
  OpenApiCustomizer documentarRutasPublicas(
      @Value("${sgp.seguridad.rutas-publicas:}") List<String> rutasPublicas) {
    return api -> {
      if (api.getPaths() == null) {
        return;
      }
      api.getPaths()
          .forEach(
              (ruta, item) ->
                  item.readOperationsMap()
                      .forEach(
                          (metodo, operacion) -> {
                            if (rutasPublicas.stream()
                                .anyMatch(r -> esPublica(ruta, metodo.name(), r))) {
                              // Una lista vacía anula el requisito Bearer global para esta
                              // operación.
                              operacion.setSecurity(List.of());
                            }
                          }));
    };
  }

  private static boolean esPublica(String ruta, String metodo, String declaracion) {
    if (declaracion.isBlank()) {
      return false;
    }
    String[] partes = declaracion.trim().split("\\s+", 2);
    if (partes.length == 2 && !metodo.equalsIgnoreCase(partes[0])) {
      return false;
    }
    return PathPatternParser.defaultInstance
        .parse(partes[partes.length - 1])
        .matches(PathContainer.parsePath(ruta));
  }
}
