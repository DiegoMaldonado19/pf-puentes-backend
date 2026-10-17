package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import com.cunoc.puentes.usuario.dto.CuentaRegistradaDTO;
import com.cunoc.puentes.usuario.dto.RegistrarEstudianteDTO;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Los endpoints de registro son públicos: ningún rol de sesión cambia lo que crean. */
@WebMvcTest(RegistroController.class)
@Import(SeguridadDePrueba.class)
class RegistroControllerTest {

  private static final String REGISTRO = "/api/v1/auth/registro";
  private static final String VERIFICAR = "/api/v1/auth/verificar-correo";
  private static final String REENVIAR = "/api/v1/auth/reenviar-verificacion";
  private static final String VALIDO =
      """
      {"nombre": "Ana Pérez", "correo": "ana@correo.com", "contrasena": "Puentes2026ok"}
      """;

  @Autowired MockMvc mvc;

  @MockitoBean RegistroService registro;
  @MockitoBean VerificacionCorreoService verificacion;

  private static MockHttpServletRequestBuilder json(String ruta, String cuerpo) {
    return post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
  }

  private static CuentaRegistradaDTO cuenta() {
    return new CuentaRegistradaDTO(
        UUID.randomUUID(),
        "Ana Pérez",
        "ana@correo.com",
        Rol.ESTUDIANTE,
        EstadoUsuario.PENDIENTE,
        false);
  }

  @Test
  void sinSesionRegistraYResponde201SinContrasena() throws Exception {
    when(registro.registrar(any())).thenReturn(cuenta());

    mvc.perform(json(REGISTRO, VALIDO))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rol").value("ESTUDIANTE"))
        .andExpect(jsonPath("$.estado").value("PENDIENTE"))
        .andExpect(jsonPath("$.correo_verificado").value(false))
        .andExpect(jsonPath("$.contrasena").doesNotExist())
        .andExpect(jsonPath("$.contrasena_hash").doesNotExist());
  }

  @ParameterizedTest
  @EnumSource(Rol.class)
  void unaSesionDeCualquierRolNoEleva(Rol rol) throws Exception {
    when(registro.registrar(any())).thenReturn(cuenta());

    mvc.perform(json(REGISTRO, VALIDO).with(como(rol)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rol").value("ESTUDIANTE"));
  }

  @Test
  void ignoraElCampoRolDelCuerpo() throws Exception {
    when(registro.registrar(any())).thenReturn(cuenta());

    mvc.perform(
            json(
                REGISTRO,
                """
                {"nombre": "Ana", "correo": "ana@correo.com",
                 "contrasena": "Puentes2026ok", "rol": "ADMINISTRADOR"}
                """))
        .andExpect(status().isCreated());

    ArgumentCaptor<RegistrarEstudianteDTO> datos =
        ArgumentCaptor.forClass(RegistrarEstudianteDTO.class);
    verify(registro).registrar(datos.capture());
    // El DTO no tiene campo de rol: lo único que el servicio recibe son nombre, correo y clave
    assertThat(datos.getValue().correo()).isEqualTo("ana@correo.com");
  }

  @Test
  void normalizaElCorreoAntesDeValidar() throws Exception {
    when(registro.registrar(any())).thenReturn(cuenta());

    mvc.perform(
            json(
                REGISTRO,
                """
                {"nombre": " Ana ", "correo": "  ANA@Correo.COM ", "contrasena": "Puentes2026ok"}
                """))
        .andExpect(status().isCreated());

    ArgumentCaptor<RegistrarEstudianteDTO> datos =
        ArgumentCaptor.forClass(RegistrarEstudianteDTO.class);
    verify(registro).registrar(datos.capture());
    assertThat(datos.getValue().correo()).isEqualTo("ana@correo.com");
    assertThat(datos.getValue().nombre()).isEqualTo("Ana");
  }

  @ParameterizedTest
  @ValueSource(strings = {"corta1", "sinnumeroaqui", "1234567890"})
  void unaContrasenaQueNoCumpleLaPoliticaResponde400EnElCampo(String contrasena) throws Exception {
    mvc.perform(
            json(
                REGISTRO,
                """
                {"nombre": "Ana", "correo": "ana@correo.com", "contrasena": "%s"}
                """
                    .formatted(contrasena)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores[0].campo").value("contrasena"))
        .andExpect(
            jsonPath("$.errores[0].mensaje").value(org.hamcrest.Matchers.containsString("10")));

    verifyNoInteractions(registro);
  }

  @Test
  void rechazaCorreoSinFormatoYCamposVacios() throws Exception {
    mvc.perform(json(REGISTRO, "{\"nombre\": \"\", \"correo\": \"no-es-correo\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores.length()").value(3));

    verifyNoInteractions(registro);
  }

  @Test
  void verificaYReenviaSinSesion() throws Exception {
    mvc.perform(json(VERIFICAR, "{\"token\": \"abc\"}")).andExpect(status().isNoContent());
    mvc.perform(json(REENVIAR, "{\"correo\": \" ANA@correo.com\"}"))
        .andExpect(status().isAccepted());

    verify(verificacion).verificar("abc");
    verify(verificacion).reenviar("ana@correo.com");
  }

  @Test
  void verificarSinTokenResponde400() throws Exception {
    mvc.perform(json(VERIFICAR, "{}")).andExpect(status().isBadRequest());
    mvc.perform(json(REENVIAR, "{\"correo\": \"x\"}")).andExpect(status().isBadRequest());
  }
}
