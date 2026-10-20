package com.cunoc.puentes.notificacion;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.notificacion.dto.ConteoNoLeidasDTO;
import com.cunoc.puentes.notificacion.dto.NotificacionDTO;
import com.cunoc.puentes.notificacion.dto.PaginaNotificacionesDTO;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificacionController.class)
@Import(NotificacionControllerTest.SeguridadPorMetodo.class)
class NotificacionControllerTest {

  private static final String USUARIO = "0192f5a0-0000-7000-8000-000000000001";
  private static final String NOTIFICACION_ID_STR = "0192f5a0-0000-7000-8000-000000000010";
  private static final UUID NOTIFICACION_ID = UUID.fromString(NOTIFICACION_ID_STR);
  private static final UUID USUARIO_ID = UUID.fromString(USUARIO);

  @TestConfiguration
  @EnableMethodSecurity
  static class SeguridadPorMetodo {}

  @Autowired MockMvc mvc;

  @MockitoBean NotificacionService servicio;

  @ParameterizedTest
  @ValueSource(strings = {"ESTUDIANTE", "PROFESIONAL_EXTERNO", "CATEDRATICO", "ADMINISTRADOR"})
  void todosLosRolesAutenticadosPuedenConsultarYModificarSusAvisos(String rol) throws Exception {
    when(servicio.listar(eq(USUARIO_ID), anyInt(), anyInt()))
        .thenReturn(new PaginaNotificacionesDTO(List.of(), 0, 20, 0, 0));
    when(servicio.contarNoLeidas(USUARIO_ID)).thenReturn(new ConteoNoLeidasDTO(0));
    when(servicio.marcarComoLeida(NOTIFICACION_ID, USUARIO_ID))
        .thenReturn(
            new NotificacionDTO(
                NOTIFICACION_ID,
                "ALERTA",
                "Mensaje",
                null,
                Instant.parse("2026-10-10T10:00:00Z"),
                Instant.parse("2026-10-10T11:00:00Z")));

    mvc.perform(get("/api/v1/notificaciones").with(user(USUARIO).roles(rol)))
        .andExpect(status().isOk());

    mvc.perform(get("/api/v1/notificaciones/no-leidas/conteo").with(user(USUARIO).roles(rol)))
        .andExpect(status().isOk());

    mvc.perform(
            patch("/api/v1/notificaciones/" + NOTIFICACION_ID_STR + "/lectura")
                .with(csrf())
                .with(user(USUARIO).roles(rol)))
        .andExpect(status().isOk());
  }

  @Test
  void sinSesionResponde401() throws Exception {
    mvc.perform(get("/api/v1/notificaciones").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());

    mvc.perform(get("/api/v1/notificaciones/no-leidas/conteo").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());

    mvc.perform(
            patch("/api/v1/notificaciones/" + NOTIFICACION_ID_STR + "/lectura")
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void principalSinUuidValidoProduceErrorControladoNo500() throws Exception {
    mvc.perform(get("/api/v1/notificaciones").with(user("usuario-no-uuid").roles("ESTUDIANTE")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").exists())
        .andExpect(jsonPath("$.title").value("Identificador de usuario inválido"));

    mvc.perform(
            get("/api/v1/notificaciones/no-leidas/conteo")
                .with(user("usuario-no-uuid").roles("ESTUDIANTE")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").exists())
        .andExpect(jsonPath("$.title").value("Identificador de usuario inválido"));
  }

  @Test
  void uuidInvalidoEnRutaResponde400() throws Exception {
    mvc.perform(
            patch("/api/v1/notificaciones/no-es-un-uuid/lectura")
                .with(csrf())
                .with(user(USUARIO).roles("ESTUDIANTE")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void notificacionInexistenteOAjenaRespondeElMismo404() throws Exception {
    when(servicio.marcarComoLeida(NOTIFICACION_ID, USUARIO_ID))
        .thenThrow(
            new NegocioException(
                HttpStatus.NOT_FOUND,
                "notificacion-no-encontrada",
                "Notificación no encontrada",
                "No existe la notificación especificada para el usuario actual."));

    mvc.perform(
            patch("/api/v1/notificaciones/" + NOTIFICACION_ID_STR + "/lectura")
                .with(csrf())
                .with(user(USUARIO).roles("ESTUDIANTE")))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type")
                .value("https://sgp.cunoc.usac.edu.gt/errores/notificacion-no-encontrada"));
  }

  @Test
  void devuelvePaginaCorrectaConEstructuraJsonYFormatoIso() throws Exception {
    Instant ahora = Instant.parse("2026-10-10T15:30:00Z");
    NotificacionDTO dto =
        new NotificacionDTO(
            NOTIFICACION_ID, "REVISION_PENDIENTE", "Tienes una revisión", "/rev/1", ahora, null);

    when(servicio.listar(USUARIO_ID, 0, 20))
        .thenReturn(new PaginaNotificacionesDTO(List.of(dto), 0, 20, 1, 1));

    mvc.perform(
            get("/api/v1/notificaciones?page=0&size=20").with(user(USUARIO).roles("ESTUDIANTE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pagina").value(0))
        .andExpect(jsonPath("$.tamano").value(20))
        .andExpect(jsonPath("$.total_elementos").value(1))
        .andExpect(jsonPath("$.total_paginas").value(1))
        .andExpect(jsonPath("$.contenido[0].id").value(NOTIFICACION_ID_STR))
        .andExpect(jsonPath("$.contenido[0].tipo").value("REVISION_PENDIENTE"))
        .andExpect(jsonPath("$.contenido[0].mensaje").value("Tienes una revisión"))
        .andExpect(jsonPath("$.contenido[0].enlace").value("/rev/1"))
        .andExpect(jsonPath("$.contenido[0].creado_en").value("2026-10-10T15:30:00Z"))
        .andExpect(jsonPath("$.contenido[0].leida_en").doesNotExist())
        .andExpect(jsonPath("$.contenido[0].usuario_id").doesNotExist());
  }

  @Test
  void rechazaPaginacionInvalidaCon400() throws Exception {
    when(servicio.listar(eq(USUARIO_ID), eq(-1), eq(20)))
        .thenThrow(
            new NegocioException(
                HttpStatus.BAD_REQUEST,
                "paginacion-invalida",
                "Paginación inválida",
                "Página negativa"));

    mvc.perform(
            get("/api/v1/notificaciones?page=-1&size=20").with(user(USUARIO).roles("ESTUDIANTE")))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type").value("https://sgp.cunoc.usac.edu.gt/errores/paginacion-invalida"));
  }

  @Test
  void rechazaDesbordamientoDeOffsetCon400() throws Exception {
    when(servicio.listar(eq(USUARIO_ID), eq(Integer.MAX_VALUE), eq(100)))
        .thenThrow(
            new NegocioException(
                HttpStatus.BAD_REQUEST,
                "paginacion-invalida",
                "Paginación inválida",
                "Desplazamiento desbordado"));

    mvc.perform(
            get("/api/v1/notificaciones?page=" + Integer.MAX_VALUE + "&size=100")
                .with(user(USUARIO).roles("ESTUDIANTE")))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type").value("https://sgp.cunoc.usac.edu.gt/errores/paginacion-invalida"));
  }

  @Test
  void devuelveConteoNoLeidas() throws Exception {
    when(servicio.contarNoLeidas(USUARIO_ID)).thenReturn(new ConteoNoLeidasDTO(7));

    mvc.perform(
            get("/api/v1/notificaciones/no-leidas/conteo").with(user(USUARIO).roles("ESTUDIANTE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(7));
  }
}
