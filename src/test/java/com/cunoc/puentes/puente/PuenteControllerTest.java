package com.cunoc.puentes.puente;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import com.cunoc.puentes.puente.dto.DepartamentoDTO;
import com.cunoc.puentes.puente.dto.MunicipioDTO;
import com.cunoc.puentes.puente.dto.PaginaPuentesDTO;
import com.cunoc.puentes.puente.dto.PuenteDTO;
import com.cunoc.puentes.puente.dto.UtmDTO;
import com.cunoc.puentes.usuario.Rol;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PuenteController.class)
@Import(SeguridadDePrueba.class)
class PuenteControllerTest {

  private static final UUID ID = UUID.fromString("0192f5a0-0000-7000-8000-000000000301");
  private static final String ALTA =
      """
      {"nombre": "Las Rosas", "departamento_codigo": "09", "municipio_codigo": "0901",
       "ruta": "CA-1", "latitud": 14.8347, "longitud": -91.518, "anio_construccion": 1985}
      """;
  private static final String EDICION =
      """
      {"nombre": "Las Rosas", "ruta": "CA-1", "latitud": 14.8347, "longitud": -91.518}
      """;
  private static final PuenteDTO PUENTE =
      new PuenteDTO(
          ID,
          "GT-09-0901-0001",
          "Las Rosas",
          "CA-1",
          new DepartamentoDTO("09", "Quetzaltenango"),
          new MunicipioDTO("0901", "Quetzaltenango", "09"),
          14.8347,
          -91.518,
          new UtmDTO(15, "N", 659463.36, 1640571.32),
          null,
          true,
          null,
          null,
          null,
          null,
          null,
          null);

  @Autowired MockMvc mvc;
  @MockitoBean PuenteService servicio;

  @Test
  void elVisitanteSinSesionVeListadoYFicha() throws Exception {
    when(servicio.listar(any(), any(), any(), any(), eq(false), eq(0), eq(20)))
        .thenReturn(new PaginaPuentesDTO(List.of(PUENTE), 0, 20, 1, 1));
    when(servicio.detalle(ID, false)).thenReturn(PUENTE);

    mvc.perform(get("/api/v1/puentes"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido[0].codigo").value("GT-09-0901-0001"))
        .andExpect(jsonPath("$.contenido[0].estado_actual").isEmpty())
        .andExpect(jsonPath("$.total_elementos").value(1));
    mvc.perform(get("/api/v1/puentes/" + ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.utm.zona").value(15))
        .andExpect(jsonPath("$.municipio.departamento_codigo").value("09"));
  }

  @Test
  void pasaLosFiltrosAlServicio() throws Exception {
    when(servicio.listar("09", "0901", EstadoFiltro.SIN_EVALUAR, "rosas", false, 2, 10))
        .thenReturn(new PaginaPuentesDTO(List.of(), 2, 10, 0, 0));

    mvc.perform(
            get("/api/v1/puentes")
                .param("departamento", "09")
                .param("municipio", "0901")
                .param("estado", "SIN_EVALUAR")
                .param("q", "rosas")
                .param("page", "2")
                .param("size", "10"))
        .andExpect(status().isOk());
  }

  @Test
  void unEstadoDesconocidoEs400() throws Exception {
    mvc.perform(get("/api/v1/puentes").param("estado", "EXCELENTE"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void soloElAdministradorVeBajas() throws Exception {
    when(servicio.listar(any(), any(), any(), any(), eq(true), anyInt(), anyInt()))
        .thenReturn(new PaginaPuentesDTO(List.of(), 0, 20, 0, 0));
    when(servicio.detalle(ID, true)).thenReturn(PUENTE);

    mvc.perform(get("/api/v1/puentes").param("incluir_bajas", "true"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/puentes").param("incluir_bajas", "true").with(como(Rol.CATEDRATICO)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/puentes").param("incluir_bajas", "true").with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/puentes/" + ID).with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk());
  }

  @Test
  void lasRutasAnidadasDeOtrosModulosNoQuedanPublicas() throws Exception {
    mvc.perform(get("/api/v1/puentes/" + ID + "/ordenes-mantenimiento"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void losCercanosPidenSesion() throws Exception {
    mvc.perform(get("/api/v1/puentes/cercanos").param("latitud", "14.8").param("longitud", "-91.5"))
        .andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void quienNoDaDeAltaNoConsultaCercanos(Rol rol) throws Exception {
    mvc.perform(
            get("/api/v1/puentes/cercanos")
                .param("latitud", "14.8")
                .param("longitud", "-91.5")
                .with(como(rol)))
        .andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ADMINISTRADOR", "CATEDRATICO"})
  void adminYCatedraticoConsultanCercanos(Rol rol) throws Exception {
    when(servicio.cercanos(14.8, -91.5, null)).thenReturn(List.of());

    mvc.perform(
            get("/api/v1/puentes/cercanos")
                .param("latitud", "14.8")
                .param("longitud", "-91.5")
                .with(como(rol)))
        .andExpect(status().isOk());
    verify(servicio).cercanos(eq(14.8), eq(-91.5), isNull());
  }

  @Test
  void crearSinSesionEs401() throws Exception {
    mvc.perform(post("/api/v1/puentes").contentType(MediaType.APPLICATION_JSON).content(ALTA))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(servicio);
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO", "CATEDRATICO"})
  void soloElAdministradorCreaYEdita(Rol rol) throws Exception {
    mvc.perform(
            post("/api/v1/puentes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ALTA)
                .with(como(rol)))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/v1/puentes/" + ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(EDICION)
                .with(como(rol)))
        .andExpect(status().isForbidden());
    verifyNoInteractions(servicio);
  }

  @Test
  void elAdministradorCreaConLocation() throws Exception {
    when(servicio.crear(any())).thenReturn(PUENTE);

    mvc.perform(
            post("/api/v1/puentes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ALTA)
                .with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/puentes/" + ID))
        .andExpect(jsonPath("$.codigo").value("GT-09-0901-0001"));
  }

  @Test
  void elAdministradorEdita() throws Exception {
    when(servicio.editar(eq(ID), any())).thenReturn(PUENTE);

    mvc.perform(
            put("/api/v1/puentes/" + ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(EDICION)
                .with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk());
  }

  @Test
  void validaObligatoriosYRangos() throws Exception {
    mvc.perform(
            post("/api/v1/puentes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nombre": " ", "departamento_codigo": "9", "municipio_codigo": "0901",
                     "latitud": 40.0, "longitud": -91.5, "anio_construccion": 1700}
                    """)
                .with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores.length()").value(5));
    verifyNoInteractions(servicio);
  }

  @Test
  void unIdInvalidoEs400() throws Exception {
    when(servicio.detalle(any(), anyBoolean())).thenReturn(PUENTE);

    mvc.perform(get("/api/v1/puentes/no-es-uuid")).andExpect(status().isBadRequest());
  }
}
