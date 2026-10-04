package com.cunoc.puentes.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(useDefaultFilters = false, properties = "sgp.cors.origenes=http://localhost:4200")
@Import(CorsConfig.class)
class CorsConfigTest {

  @Autowired MockMvc mvc;

  @Test
  void aceptaElOrigenConfigurado() throws Exception {
    mvc.perform(preflightDesde("http://localhost:4200"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
  }

  @Test
  void rechazaCualquierOtroOrigen() throws Exception {
    mvc.perform(preflightDesde("https://otro-sitio.com")).andExpect(status().isForbidden());
  }

  private static MockHttpServletRequestBuilder preflightDesde(String origen) {
    return options("/api/v1/puentes")
        .header(HttpHeaders.ORIGIN, origen)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
  }
}
