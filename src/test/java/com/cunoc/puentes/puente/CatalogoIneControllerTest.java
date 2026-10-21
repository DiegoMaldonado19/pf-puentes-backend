package com.cunoc.puentes.puente;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import com.cunoc.puentes.puente.dto.DepartamentoDTO;
import com.cunoc.puentes.puente.dto.MunicipioDTO;
import com.cunoc.puentes.usuario.Rol;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CatalogoIneController.class)
@Import(SeguridadDePrueba.class)
class CatalogoIneControllerTest {

  @Autowired MockMvc mvc;
  @MockitoBean CatalogoIneService servicio;

  @Test
  void elVisitanteSinSesionConsultaElCatalogo() throws Exception {
    when(servicio.listarDepartamentos())
        .thenReturn(List.of(new DepartamentoDTO("09", "Quetzaltenango")));
    when(servicio.listarMunicipios("09"))
        .thenReturn(List.of(new MunicipioDTO("0901", "Quetzaltenango", "09")));

    mvc.perform(get("/api/v1/departamentos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].codigo").value("09"))
        .andExpect(jsonPath("$[0].nombre").value("Quetzaltenango"));
    mvc.perform(get("/api/v1/departamentos/09/municipios"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].codigo").value("0901"))
        .andExpect(jsonPath("$[0].departamento_codigo").value("09"));
  }

  @ParameterizedTest
  @EnumSource(Rol.class)
  void todosLosRolesConsultanElCatalogo(Rol rol) throws Exception {
    when(servicio.listarDepartamentos()).thenReturn(List.of());

    mvc.perform(get("/api/v1/departamentos").with(como(rol))).andExpect(status().isOk());
  }

  @Test
  void unDepartamentoInexistenteResponde404EnRfc7807() throws Exception {
    when(servicio.listarMunicipios("99"))
        .thenThrow(
            new NegocioException(
                HttpStatus.NOT_FOUND, "departamento-no-encontrado", "No encontrado", "No existe"));

    mvc.perform(get("/api/v1/departamentos/99/municipios"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }
}
