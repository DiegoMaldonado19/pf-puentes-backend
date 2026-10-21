package com.cunoc.puentes.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import com.cunoc.puentes.usuario.dto.AceptarInvitacionDTO;
import com.cunoc.puentes.usuario.dto.CuentaRegistradaDTO;
import com.cunoc.puentes.usuario.dto.InvitacionPublicaDTO;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Los endpoints de aceptación son públicos: el token va en el cuerpo, nunca en la URL. */
@WebMvcTest(AceptacionInvitacionController.class)
@Import(SeguridadDePrueba.class)
class AceptacionInvitacionControllerTest {

  private static final String CONSULTAR = "/api/v1/auth/invitaciones/consultar";
  private static final String ACEPTAR = "/api/v1/auth/invitaciones/aceptar";
  private static final String CONTRASENA = "Puentes2026ok";
  private static final String TOKEN = "token-de-prueba-123";

  @Autowired MockMvc mvc;

  @MockitoBean InvitacionService invitaciones;

  private static MockHttpServletRequestBuilder json(String ruta, String cuerpo) {
    return post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
  }

  @Test
  void sinSesionConsultaYAceptaSinExponerLaContrasena() throws Exception {
    when(invitaciones.consultar(TOKEN))
        .thenReturn(
            new InvitacionPublicaDTO(
                "prof@correo.com", Rol.CATEDRATICO, Instant.parse("2026-10-27T15:00:00Z")));
    when(invitaciones.aceptar(any()))
        .thenReturn(
            new CuentaRegistradaDTO(
                UUID.randomUUID(),
                "Ana Pérez",
                "prof@correo.com",
                Rol.CATEDRATICO,
                EstadoUsuario.ACTIVO,
                true));

    mvc.perform(json(CONSULTAR, "{\"token\": \"%s\"}".formatted(TOKEN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correo").value("prof@correo.com"))
        .andExpect(jsonPath("$.rol").value("CATEDRATICO"))
        .andExpect(jsonPath("$.token").doesNotExist());

    String cuerpo =
        mvc.perform(
                json(
                    ACEPTAR,
                    """
                    {"token": "%s", "nombre": "Ana Pérez", "contrasena": "%s"}
                    """
                        .formatted(TOKEN, CONTRASENA)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.rol").value("CATEDRATICO"))
            .andExpect(jsonPath("$.contrasena").doesNotExist())
            .andExpect(jsonPath("$.contrasena_hash").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(cuerpo).doesNotContain(CONTRASENA);

    ArgumentCaptor<AceptarInvitacionDTO> datos =
        ArgumentCaptor.forClass(AceptarInvitacionDTO.class);
    verify(invitaciones).aceptar(datos.capture());
    assertThat(datos.getValue().token()).isEqualTo(TOKEN);
  }

  @Test
  void unCuerpoInvalidoResponde400SinLlamarAlServicio() throws Exception {
    mvc.perform(json(CONSULTAR, "{}")).andExpect(status().isBadRequest());
    mvc.perform(
            json(
                ACEPTAR,
                """
                {"token": "abc", "nombre": "", "contrasena": "corta1"}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores[0].campo").exists());

    verifyNoInteractions(invitaciones);
  }
}
