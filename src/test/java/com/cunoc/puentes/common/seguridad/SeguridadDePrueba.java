package com.cunoc.puentes.common.seguridad;

import static org.mockito.Mockito.mock;

import com.cunoc.puentes.usuario.EstadoCuentaService;
import java.util.Map;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * Contrato 12: la seguridad real (cadena de filtros, {@code @PreAuthorize}, 401 y 403 en RFC 7807)
 * para una prueba de controlador.
 *
 * <pre>{@code
 * @WebMvcTest(MiController.class)
 * @Import(SeguridadDePrueba.class)
 * class MiControllerTest { ... .with(como(Rol.ESTUDIANTE)) ... }
 * }</pre>
 *
 * Para probar el filtro JWT con tokens, declara {@code @MockitoBean EstadoCuentaService}.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(ConfiguracionSeguridad.class)
public class SeguridadDePrueba {

  static final String SECRETO = "clave-de-pruebas-de-al-menos-32-bytes!";

  /**
   * MockMvc comparte IP y contexto: el tope de producción se prueba en {@code LimitadorDeTasa*}.
   * Queda por debajo de {@code @WebMvcTest(properties=…)} para que esa clase pueda fijar reglas.
   */
  @Bean
  static BeanFactoryPostProcessor sinTopeDeTasaEnElSlice(ConfigurableEnvironment entorno) {
    return _ -> {
      MapPropertySource vacio =
          new MapPropertySource("sgp.limite-tasa.pruebas", Map.of("sgp.limite-tasa.reglas", ""));
      String aplicacion = null;
      for (PropertySource<?> fuente : entorno.getPropertySources()) {
        if (fuente.getName().contains("application.properties")) {
          aplicacion = fuente.getName();
          break;
        }
      }
      if (aplicacion != null) {
        entorno.getPropertySources().addBefore(aplicacion, vacio);
      } else {
        entorno.getPropertySources().addFirst(vacio);
      }
    };
  }

  @Bean
  TokenService tokenService() {
    return new TokenService(SECRETO);
  }

  @Bean
  EstadoCuentaService estadoCuentaService() {
    return mock(EstadoCuentaService.class);
  }
}
