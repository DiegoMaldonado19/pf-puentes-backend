package com.cunoc.puentes.common.seguridad;

import com.cunoc.puentes.usuario.EstadoCuentaService;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Seguridad stateless con JWT (DT-ARQ-05, DT-SEC-01, DT-SEC-03). Todo es privado salvo lo que se
 * declare público aquí: las rutas de {@code sgp.seguridad.rutas-publicas} y, fuera de producción,
 * la documentación OpenAPI (DT-BE-12).
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class ConfiguracionSeguridad {

  /** DT-SEC-05, RN-USR-06 */
  static final int FACTOR_BCRYPT = 12;

  private static final String DOCS = "/api/docs";
  private static final String HEALTH = "/actuator/health";

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(FACTOR_BCRYPT);
  }

  /**
   * @param rutasPublicas {@code "/ruta/**"} para cualquier método, o {@code "GET /ruta/**"}
   */
  @Bean
  SecurityFilterChain cadenaDeFiltros(
      HttpSecurity http,
      TokenService tokens,
      EstadoCuentaService cuentas,
      JsonMapper json,
      Environment entorno,
      @Value("${sgp.seguridad.rutas-publicas:}") List<String> rutasPublicas,
      @Value("${sgp.limite-tasa.reglas:}") List<String> reglasTasa)
      throws Exception {
    RespuestasSeguridad respuestas = new RespuestasSeguridad(json);
    return http.csrf(AbstractHttpConfigurer::disable) // API stateless con Authorization
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(respuestas.noAutenticado())
                    .accessDeniedHandler(respuestas.accesoDenegado()))
        .addFilterBefore(
            new LimitadorDeTasa(reglasTasa, Clock.systemUTC(), respuestas),
            UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(new FiltroJwt(tokens, cuentas), UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(
            rutas -> {
              // El healthcheck del despliegue consulta el puerto 8081 sin token (DT-DEP-10)
              rutas.requestMatchers(HEALTH, HEALTH + "/**").permitAll();
              rutasPublicas.forEach(declaracion -> declararPublica(rutas, declaracion));
              if (!entorno.acceptsProfiles(Profiles.of("prod"))) {
                rutas.requestMatchers(DOCS, DOCS + "/**").permitAll();
              }
              rutas.anyRequest().authenticated();
            })
        .build();
  }

  private static void declararPublica(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          rutas,
      String declaracion) {
    String[] partes = declaracion.trim().split("\\s+", 2);
    if (partes.length == 2) {
      rutas.requestMatchers(HttpMethod.valueOf(partes[0].toUpperCase()), partes[1]).permitAll();
    } else {
      rutas.requestMatchers(partes[0]).permitAll();
    }
  }
}
