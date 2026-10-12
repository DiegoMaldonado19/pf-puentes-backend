package com.cunoc.puentes.common.seguridad;

import static org.mockito.Mockito.mock;

import com.cunoc.puentes.usuario.EstadoCuentaService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

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

  @Bean
  TokenService tokenService() {
    return new TokenService(SECRETO);
  }

  @Bean
  EstadoCuentaService estadoCuentaService() {
    return mock(EstadoCuentaService.class);
  }
}
