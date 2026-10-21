package com.cunoc.puentes.puente;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import com.cunoc.puentes.puente.dto.DepartamentoDTO;
import com.cunoc.puentes.puente.dto.MunicipioDTO;
import com.cunoc.puentes.puente.dto.PaginaSolicitudesDTO;
import com.cunoc.puentes.puente.dto.SolicitudPuenteDTO;
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

@WebMvcTest(SolicitudPuenteController.class)
@Import(SeguridadDePrueba.class)
class SolicitudPuenteControllerTest {

  private static final String RUTA = "/api/v1/solicitudes-puente";
  private static final UUID ID = UUID.fromString("0192f5a0-0000-7000-8000-000000000401");
  private static final UUID CATEDRATICO = UUID.fromString("0192f5a0-0000-7000-8000-000000000402");
  private static final UUID ADMIN = UUID.fromString("0192f5a0-0000-7000-8000-000000000404");
  private static final String SOLICITUD =
      """
      {"nombre": "Las Rosas", "departamento_codigo": "09", "municipio_codigo": "0901",
       "ruta": "CA-1", "latitud": 14.8347, "longitud": -91.518}
      """;
  private static final SolicitudPuenteDTO DTO =
      new SolicitudPuenteDTO(
          ID,
          CATEDRATICO,
          "Las Rosas",
          new DepartamentoDTO("09", "Quetzaltenango"),
          new MunicipioDTO("0901", "Quetzaltenango", "09"),
          "CA-1",
          14.8347,
          -91.518,
          new UtmDTO(15, "N", 659463.36, 1640571.32),
          EstadoSolicitud.PENDIENTE,
          null,
          null,
          null,
          null,
          null);

  @Autowired MockMvc mvc;
  @MockitoBean SolicitudPuenteService servicio;

  @Test
  void nadaEsPublico() throws Exception {
    mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
    mvc.perform(get(RUTA + "/" + ID)).andExpect(status().isUnauthorized());
    mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(servicio);
  }

  @Test
  void elCatedraticoSolicitaComoElMismo() throws Exception {
    when(servicio.solicitar(eq(CATEDRATICO), any())).thenReturn(DTO);

    mvc.perform(
            post(RUTA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(SOLICITUD)
                .with(como(Rol.CATEDRATICO, CATEDRATICO)))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", RUTA + "/" + ID))
        .andExpect(jsonPath("$.estado").value("PENDIENTE"))
        .andExpect(jsonPath("$.catedratico_id").value(CATEDRATICO.toString()));
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO", "ADMINISTRADOR"})
  void soloElCatedraticoSolicita(Rol rol) throws Exception {
    mvc.perform(
            post(RUTA).contentType(MediaType.APPLICATION_JSON).content(SOLICITUD).with(como(rol)))
        .andExpect(status().isForbidden());
    verifyNoInteractions(servicio);
  }

  @Test
  void elCatedraticoVeLasSuyasYElAdministradorTodas() throws Exception {
    when(servicio.listar(CATEDRATICO, false, null, 0, 20))
        .thenReturn(new PaginaSolicitudesDTO(List.of(DTO), 0, 20, 1, 1));
    when(servicio.listar(ADMIN, true, EstadoSolicitud.PENDIENTE, 0, 20))
        .thenReturn(new PaginaSolicitudesDTO(List.of(DTO), 0, 20, 1, 1));
    when(servicio.detalle(ID, CATEDRATICO, false)).thenReturn(DTO);

    mvc.perform(get(RUTA).with(como(Rol.CATEDRATICO, CATEDRATICO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elementos").value(1));
    mvc.perform(get(RUTA).param("estado", "PENDIENTE").with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk());
    mvc.perform(get(RUTA + "/" + ID).with(como(Rol.CATEDRATICO, CATEDRATICO)))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void otrosRolesNoConsultanSolicitudes(Rol rol) throws Exception {
    mvc.perform(get(RUTA).with(como(rol))).andExpect(status().isForbidden());
    mvc.perform(get(RUTA + "/" + ID).with(como(rol))).andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO", "CATEDRATICO"})
  void soloElAdministradorResuelve(Rol rol) throws Exception {
    mvc.perform(
            post(RUTA + "/" + ID + "/aprobacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(como(rol)))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(RUTA + "/" + ID + "/rechazo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"No\"}")
                .with(como(rol)))
        .andExpect(status().isForbidden());
    verifyNoInteractions(servicio);
  }

  @Test
  void elAdministradorApruebaYRechazaConMotivo() throws Exception {
    when(servicio.aprobar(eq(ID), eq(ADMIN), any())).thenReturn(DTO);
    when(servicio.rechazar(eq(ID), eq(ADMIN), any())).thenReturn(DTO);

    mvc.perform(
            post(RUTA + "/" + ID + "/aprobacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmar_duplicado\": true}")
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk());
    mvc.perform(
            post(RUTA + "/" + ID + "/rechazo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"\"}")
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(RUTA + "/" + ID + "/rechazo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"Ya existe\"}")
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk());
  }
}
