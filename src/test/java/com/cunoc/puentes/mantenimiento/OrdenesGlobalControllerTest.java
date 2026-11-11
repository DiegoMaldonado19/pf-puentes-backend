package com.cunoc.puentes.mantenimiento;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import com.cunoc.puentes.mantenimiento.dto.PaginaOrdenesGlobalDTO;
import com.cunoc.puentes.usuario.Rol;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrdenesGlobalController.class)
@Import(SeguridadDePrueba.class)
class OrdenesGlobalControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private ConsultaOrdenesGlobalService consulta;

  @Test
  @DisplayName("El catedrático lista las órdenes de todos los puentes (B9)")
  void listar_catedratico_retorna200() throws Exception {
    when(consulta.listar(any(), anyInt(), anyInt()))
        .thenReturn(new PaginaOrdenesGlobalDTO(List.of(), 0, 20, 0, 0));

    mvc.perform(get("/api/v1/ordenes-mantenimiento").with(como(Rol.CATEDRATICO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
  }

  @Test
  @DisplayName("El estudiante no ve el listado global de órdenes (403)")
  void listar_estudiante_retorna403() throws Exception {
    mvc.perform(get("/api/v1/ordenes-mantenimiento").with(como(Rol.ESTUDIANTE)))
        .andExpect(status().isForbidden());
  }
}
