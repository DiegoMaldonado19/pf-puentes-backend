package com.cunoc.puentes.common.seguridad;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DT-SEC-07: el N+1 de la misma IP responde 429; otra IP y las rutas sin regla no se ven afectadas.
 */
@WebMvcTest(
    controllers = LimitadorDeTasaFiltroTest.ControladorPrueba.class,
    properties = {
      "sgp.seguridad.rutas-publicas=POST /api/v1/prueba/login, GET /api/v1/prueba/libre",
      "sgp.limite-tasa.reglas=POST /api/v1/prueba/login=2/PT1M"
    })
@Import({SeguridadDePrueba.class, LimitadorDeTasaFiltroTest.ControladorPrueba.class})
class LimitadorDeTasaFiltroTest {

  private static final String LOGIN = "/api/v1/prueba/login";
  private static final String LIBRE = "/api/v1/prueba/libre";

  @Autowired MockMvc mvc;

  @Test
  void laTerceraPeticionDeLaMismaIpResponde429ConRetryAfter() throws Exception {
    mvc.perform(post(LOGIN).with(ip("203.0.113.10"))).andExpect(status().isOk());
    mvc.perform(post(LOGIN).with(ip("203.0.113.10"))).andExpect(status().isOk());

    mvc.perform(post(LOGIN).with(ip("203.0.113.10")))
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(endsWith("/demasiadas-solicitudes")))
        .andExpect(jsonPath("$.status").value(429))
        .andExpect(header().exists("Retry-After"));
  }

  @Test
  void otraIpNoComparteElTope() throws Exception {
    mvc.perform(post(LOGIN).with(ip("203.0.113.30"))).andExpect(status().isOk());
    mvc.perform(post(LOGIN).with(ip("203.0.113.30"))).andExpect(status().isOk());
    mvc.perform(post(LOGIN).with(ip("203.0.113.30"))).andExpect(status().isTooManyRequests());

    mvc.perform(post(LOGIN).with(ip("203.0.113.31"))).andExpect(status().isOk());
  }

  @Test
  void unaRutaSinReglaNoSeLimita() throws Exception {
    for (int i = 0; i < 5; i++) {
      mvc.perform(get(LIBRE).with(ip("203.0.113.10"))).andExpect(status().isOk());
    }
  }

  private static RequestPostProcessor ip(String ip) {
    return request -> {
      request.setRemoteAddr(ip);
      return request;
    };
  }

  @RestController
  static class ControladorPrueba {

    @PostMapping("/api/v1/prueba/login")
    String login() {
      return "ok";
    }

    @GetMapping("/api/v1/prueba/libre")
    String libre() {
      return "ok";
    }
  }
}
