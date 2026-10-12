package com.cunoc.puentes.common.seguridad;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.usuario.Rol;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ejemplo para el resto del equipo (contrato 12, DT-CAL-03): una prueba de controlador con la
 * seguridad real, que verifica 401, 403 y 2xx por rol sin infraestructura propia. Copia el patrón:
 * {@code @Import(SeguridadDePrueba.class)} y {@code .with(como(rol))}.
 */
@WebMvcTest(EjemploPruebaDeRolTest.ControladorDeEjemplo.class)
@Import({SeguridadDePrueba.class, EjemploPruebaDeRolTest.ControladorDeEjemplo.class})
class EjemploPruebaDeRolTest {

  private static final String SOLO_ADMIN = "/api/v1/ejemplo/administracion";
  private static final String QUIEN_INSPECCIONA = "/api/v1/ejemplo/inspecciones";

  @Autowired MockMvc mvc;

  @Test
  void sinSesionResponde401() throws Exception {
    mvc.perform(get(SOLO_ADMIN)).andExpect(status().isUnauthorized());
    mvc.perform(get(QUIEN_INSPECCIONA)).andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO", "CATEDRATICO"})
  void quienNoEsAdministradorRecibe403EnLaAdministracion(Rol rol) throws Exception {
    mvc.perform(get(SOLO_ADMIN).with(como(rol)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  void elAdministradorAccedeALaAdministracion() throws Exception {
    mvc.perform(get(SOLO_ADMIN).with(como(Rol.ADMINISTRADOR))).andExpect(status().isOk());
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO", "CATEDRATICO"})
  void quienInspeccionaRecibe2xx(Rol rol) throws Exception {
    mvc.perform(get(QUIEN_INSPECCIONA).with(como(rol))).andExpect(status().isOk());
  }

  @Test
  void elAdministradorNoInspeccionaYRecibe403() throws Exception {
    mvc.perform(get(QUIEN_INSPECCIONA).with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isForbidden());
  }

  @Test
  void laPruebaElijeElIdDelUsuarioQueLlama() throws Exception {
    UUID catedratico = UUID.fromString("0192f5a0-0000-7000-8000-0000000000c1");

    mvc.perform(get(QUIEN_INSPECCIONA).with(como(Rol.CATEDRATICO, catedratico)))
        .andExpect(content().string(catedratico.toString()));
  }

  @RestController
  static class ControladorDeEjemplo {

    @GetMapping("/api/v1/ejemplo/administracion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    String administracion() {
      return "ok";
    }

    @GetMapping("/api/v1/ejemplo/inspecciones")
    @PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO')")
    String inspecciones(Principal usuario) {
      return usuario.getName();
    }
  }
}
