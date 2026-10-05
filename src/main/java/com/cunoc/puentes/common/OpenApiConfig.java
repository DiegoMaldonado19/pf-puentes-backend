package com.cunoc.puentes.common;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/** DT-BE-12. Servidor relativo: "Try it out" usa el origen de la página, con su puerto. */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
    info =
        @Info(
            title = "API del Sistema de Gestión de Puentes",
            version = "v1",
            description = "Los errores responden en RFC 7807 (application/problem+json)."),
    servers = @Server(url = "/"))
class OpenApiConfig {}
