package com.cunoc.puentes.common;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** DT-SEC-06: solo los orígenes de CORS_ALLOWED_ORIGINS, nunca {@code *}. */
@Configuration(proxyBeanMethods = false)
class CorsConfig {

  // No cambiar nombre ni tipo: así Spring Security activa CORS en cualquier SecurityFilterChain
  @Bean
  UrlBasedCorsConfigurationSource corsConfigurationSource(
      @Value("${sgp.cors.origenes}") List<String> origenes) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(origenes);
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
    cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
    cors.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
    fuente.registerCorsConfiguration("/**", cors);
    return fuente;
  }
}
