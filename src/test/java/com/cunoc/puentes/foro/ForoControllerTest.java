package com.cunoc.puentes.foro;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import com.cunoc.puentes.foro.dto.ComentarioDTO;
import com.cunoc.puentes.foro.dto.CrearComentarioDTO;
import com.cunoc.puentes.foro.dto.CrearHiloDTO;
import com.cunoc.puentes.foro.dto.EditarComentarioDTO;
import com.cunoc.puentes.foro.dto.HiloDTO;
import com.cunoc.puentes.foro.dto.PaginaComentariosDTO;
import com.cunoc.puentes.foro.dto.PaginaHilosDTO;
import com.cunoc.puentes.foro.dto.PaginaPuentesForoDTO;
import com.cunoc.puentes.foro.dto.PuenteForoDTO;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ForoController.class)
@Import(SeguridadDePrueba.class)
class ForoControllerTest {

  private static final String USUARIO_STR = "0192f5a0-0000-7000-8000-000000000001";
  private static final UUID USUARIO_ID = UUID.fromString(USUARIO_STR);
  private static final String PUENTE_STR = "0192f5a0-0000-7000-8000-000000000010";
  private static final UUID PUENTE_ID = UUID.fromString(PUENTE_STR);
  private static final String HILO_STR = "0192f5a0-0000-7000-8000-000000000020";
  private static final UUID HILO_ID = UUID.fromString(HILO_STR);
  private static final String COMENTARIO_STR = "0192f5a0-0000-7000-8000-000000000030";
  private static final UUID COMENTARIO_ID = UUID.fromString(COMENTARIO_STR);

  @Autowired MockMvc mvc;

  @MockitoBean ForoService servicio;

  @Test
  void lecturasSonPublicasYAnonimas() throws Exception {
    when(servicio.listarForos(any(), anyInt(), anyInt()))
        .thenReturn(
            new PaginaPuentesForoDTO(
                List.of(
                    new PuenteForoDTO(
                        PUENTE_ID,
                        "GT-09-0901-0001",
                        "Puente Los Chocoyos",
                        "CA-1 Occidente",
                        "Quetzaltenango",
                        "Quetzaltenango",
                        "BUENO",
                        2L)),
                0,
                12,
                1,
                1));
    when(servicio.listarHilos(eq(PUENTE_ID), anyInt(), anyInt(), any()))
        .thenReturn(new PaginaHilosDTO(List.of(), 0, 20, 0, 0));
    when(servicio.obtenerHilo(HILO_ID))
        .thenReturn(
            new HiloDTO(
                HILO_ID,
                PUENTE_ID,
                "Título de prueba",
                CategoriaHilo.CONSULTA,
                USUARIO_ID,
                Instant.parse("2026-10-11T12:00:00Z"),
                false,
                "María José"));
    when(servicio.listarComentarios(eq(HILO_ID), anyInt(), anyInt()))
        .thenReturn(new PaginaComentariosDTO(List.of(), 0, 50, 0, 0));
    when(servicio.listarRespuestas(eq(COMENTARIO_ID), anyInt(), anyInt()))
        .thenReturn(new PaginaComentariosDTO(List.of(), 0, 20, 0, 0));

    mvc.perform(get("/api/v1/foro/puentes"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido[0].id").value(PUENTE_STR))
        .andExpect(jsonPath("$.contenido[0].cantidad_hilos").value(2));

    mvc.perform(get("/api/v1/puentes/" + PUENTE_STR + "/hilos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido").isArray())
        .andExpect(jsonPath("$.total_elementos").value(0));

    mvc.perform(get("/api/v1/hilos/" + HILO_STR))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(HILO_STR))
        .andExpect(jsonPath("$.puente_id").value(PUENTE_STR))
        .andExpect(jsonPath("$.categoria").value("consulta"))
        .andExpect(jsonPath("$.cerrado").value(false))
        .andExpect(jsonPath("$.autor_nombre").value("María José"));

    mvc.perform(get("/api/v1/hilos/" + HILO_STR + "/comentarios"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido").isArray());

    mvc.perform(get("/api/v1/comentarios/" + COMENTARIO_STR + "/respuestas"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido").isArray());
  }

  @Test
  void escriturasSinSesionDevuelven401() throws Exception {
    mvc.perform(
            post("/api/v1/puentes/" + PUENTE_STR + "/hilos")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"titulo": "Nuevo hilo", "categoria": "consulta"}
                    """))
        .andExpect(status().isUnauthorized());

    mvc.perform(
            post("/api/v1/hilos/" + HILO_STR + "/comentarios")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"texto": "Nuevo comentario"}
                    """))
        .andExpect(status().isUnauthorized());

    mvc.perform(
            patch("/api/v1/comentarios/" + COMENTARIO_STR)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"texto": "Texto editado"}
                    """))
        .andExpect(status().isUnauthorized());

    mvc.perform(delete("/api/v1/comentarios/" + COMENTARIO_STR).with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ESTUDIANTE", "PROFESIONAL_EXTERNO", "CATEDRATICO", "ADMINISTRADOR"})
  void todosLosRolesAutenticadosPuedenCrearHilosYComentarios(String rol) throws Exception {
    HiloDTO hiloDTO =
        new HiloDTO(
            HILO_ID,
            PUENTE_ID,
            "Fisura en estribo",
            CategoriaHilo.DISCUSION_TECNICA,
            USUARIO_ID,
            Instant.parse("2026-10-11T12:00:00Z"),
            false);
    when(servicio.crearHilo(eq(PUENTE_ID), eq(USUARIO_ID), any(CrearHiloDTO.class)))
        .thenReturn(hiloDTO);

    ComentarioDTO comentarioDTO =
        new ComentarioDTO(
            UUID.randomUUID(),
            HILO_ID,
            USUARIO_ID,
            "Texto del comentario",
            Instant.parse("2026-10-11T12:05:00Z"));
    when(servicio.crearComentario(eq(HILO_ID), eq(USUARIO_ID), any(CrearComentarioDTO.class)))
        .thenReturn(comentarioDTO);

    mvc.perform(
            post("/api/v1/puentes/" + PUENTE_STR + "/hilos")
                .with(csrf())
                .with(user(USUARIO_STR).roles(rol))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"titulo": "Fisura en estribo", "categoria": "discusion_tecnica"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(HILO_STR))
        .andExpect(jsonPath("$.categoria").value("discusion_tecnica"));

    mvc.perform(
            post("/api/v1/hilos/" + HILO_STR + "/comentarios")
                .with(csrf())
                .with(user(USUARIO_STR).roles(rol))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"texto": "Texto del comentario"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.hilo_id").value(HILO_STR));
  }

  @Test
  void crearRespuestaConPadreIdDevuelve201() throws Exception {
    ComentarioDTO respuestaDTO =
        new ComentarioDTO(
            UUID.randomUUID(),
            HILO_ID,
            USUARIO_ID,
            "Respuesta directa",
            Instant.parse("2026-10-11T12:10:00Z"),
            COMENTARIO_ID,
            false);
    when(servicio.crearComentario(eq(HILO_ID), eq(USUARIO_ID), any(CrearComentarioDTO.class)))
        .thenReturn(respuestaDTO);

    mvc.perform(
            post("/api/v1/hilos/" + HILO_STR + "/comentarios")
                .with(csrf())
                .with(user(USUARIO_STR).roles("ESTUDIANTE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "texto": "Respuesta directa",
                      "padre_id": "%s"
                    }
                    """
                        .formatted(COMENTARIO_STR)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.padre_id").value(COMENTARIO_STR))
        .andExpect(jsonPath("$.eliminado").value(false));
  }

  @Test
  void editarComentarioDevuelve200() throws Exception {
    ComentarioDTO editadoDTO =
        new ComentarioDTO(
            COMENTARIO_ID,
            HILO_ID,
            USUARIO_ID,
            "Texto editado dentro de 15 minutos",
            Instant.parse("2026-10-11T12:05:00Z"),
            null,
            false);
    when(servicio.editarComentario(
            eq(COMENTARIO_ID), eq(USUARIO_ID), any(EditarComentarioDTO.class)))
        .thenReturn(editadoDTO);

    mvc.perform(
            patch("/api/v1/comentarios/" + COMENTARIO_STR)
                .with(csrf())
                .with(user(USUARIO_STR).roles("ESTUDIANTE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"texto": "Texto editado dentro de 15 minutos"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.texto").value("Texto editado dentro de 15 minutos"));
  }

  @Test
  void eliminarComentarioDevuelve204() throws Exception {
    doNothing().when(servicio).eliminarComentario(eq(COMENTARIO_ID), eq(USUARIO_ID));

    mvc.perform(
            delete("/api/v1/comentarios/" + COMENTARIO_STR)
                .with(csrf())
                .with(user(USUARIO_STR).roles("ESTUDIANTE")))
        .andExpect(status().isNoContent());
  }

  @Test
  void principalMalformadoProduce400Controlado() throws Exception {
    mvc.perform(
            post("/api/v1/puentes/" + PUENTE_STR + "/hilos")
                .with(csrf())
                .with(user("no-es-uuid-valido").roles("ESTUDIANTE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"titulo": "Nuevo hilo", "categoria": "consulta"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type")
                .value("https://sgp.cunoc.usac.edu.gt/errores/identificador-usuario-invalido"));
  }

  @Test
  void validacionesBeanRechazanCuerpoInvalido() throws Exception {
    mvc.perform(
            post("/api/v1/puentes/" + PUENTE_STR + "/hilos")
                .with(csrf())
                .with(user(USUARIO_STR).roles("ESTUDIANTE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"titulo": "", "categoria": "consulta"}
                    """))
        .andExpect(status().isBadRequest());

    mvc.perform(
            patch("/api/v1/comentarios/" + COMENTARIO_STR)
                .with(csrf())
                .with(user(USUARIO_STR).roles("ESTUDIANTE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"texto": ""}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void comentarEnHiloCerradoDevuelve409() throws Exception {
    when(servicio.crearComentario(eq(HILO_ID), eq(USUARIO_ID), any(CrearComentarioDTO.class)))
        .thenThrow(
            new NegocioException(
                HttpStatus.CONFLICT,
                "hilo-cerrado",
                "Hilo cerrado",
                "No se pueden publicar comentarios en un hilo cerrado."));

    mvc.perform(
            post("/api/v1/hilos/" + HILO_STR + "/comentarios")
                .with(csrf())
                .with(user(USUARIO_STR).roles("ESTUDIANTE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"texto": "Comentario tardío"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("https://sgp.cunoc.usac.edu.gt/errores/hilo-cerrado"));
  }

  @Test
  void recursoInexistenteDevuelve404() throws Exception {
    when(servicio.obtenerHilo(HILO_ID))
        .thenThrow(
            new NegocioException(
                HttpStatus.NOT_FOUND,
                "hilo-no-encontrado",
                "Hilo no encontrado",
                "El hilo solicitado no existe o no pertenece al foro permanente."));

    mvc.perform(get("/api/v1/hilos/" + HILO_STR))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type").value("https://sgp.cunoc.usac.edu.gt/errores/hilo-no-encontrado"));
  }

  @Test
  void uuidInvalidoEnRutaDevuelve400() throws Exception {
    mvc.perform(get("/api/v1/puentes/no-es-uuid/hilos")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/hilos/no-es-uuid")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/comentarios/no-es-uuid/respuestas"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void moderacionAnonimaDevuelve401() throws Exception {
    mvc.perform(
            patch("/api/v1/comentarios/" + COMENTARIO_STR + "/ocultamiento")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motivo": "Spam"}
                    """))
        .andExpect(status().isUnauthorized());

    mvc.perform(
            patch("/api/v1/hilos/" + HILO_STR + "/cierre")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motivo": "Tema resuelto"}
                    """))
        .andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void moderacionSinRolAutorizadoDevuelve403(String rol) throws Exception {
    mvc.perform(
            patch("/api/v1/comentarios/" + COMENTARIO_STR + "/ocultamiento")
                .with(csrf())
                .with(user(USUARIO_STR).roles(rol))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motivo": "Contenido inapropiado"}
                    """))
        .andExpect(status().isForbidden());

    mvc.perform(
            patch("/api/v1/hilos/" + HILO_STR + "/cierre")
                .with(csrf())
                .with(user(USUARIO_STR).roles(rol))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motivo": "Tema cerrado"}
                    """))
        .andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @ValueSource(strings = {"CATEDRATICO", "ADMINISTRADOR"})
  void moderacionConRolAutorizadoDevuelve204(String rol) throws Exception {
    mvc.perform(
            patch("/api/v1/comentarios/" + COMENTARIO_STR + "/ocultamiento")
                .with(csrf())
                .with(user(USUARIO_STR).roles(rol))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motivo": "Contenido ofensivo verificado"}
                    """))
        .andExpect(status().isNoContent());

    mvc.perform(
            patch("/api/v1/hilos/" + HILO_STR + "/cierre")
                .with(csrf())
                .with(user(USUARIO_STR).roles(rol))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motivo": "Discusión técnica concluida"}
                    """))
        .andExpect(status().isNoContent());
  }

  @Test
  void moderacionConMotivoInvalidoDevuelve400() throws Exception {
    mvc.perform(
            patch("/api/v1/comentarios/" + COMENTARIO_STR + "/ocultamiento")
                .with(csrf())
                .with(user(USUARIO_STR).roles("CATEDRATICO"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motivo": "   "}
                    """))
        .andExpect(status().isBadRequest());

    mvc.perform(
            patch("/api/v1/hilos/" + HILO_STR + "/cierre")
                .with(csrf())
                .with(user(USUARIO_STR).roles("ADMINISTRADOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motivo": ""}
                    """))
        .andExpect(status().isBadRequest());

    mvc.perform(
            patch("/api/v1/comentarios/" + COMENTARIO_STR + "/ocultamiento")
                .with(csrf())
                .with(user(USUARIO_STR).roles("CATEDRATICO"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void moderacionRecursoInexistenteDevuelve404() throws Exception {
    when(servicio.ocultarComentario(eq(COMENTARIO_ID), any()))
        .thenThrow(
            new NegocioException(
                HttpStatus.NOT_FOUND,
                "comentario-no-encontrado",
                "Comentario no encontrado",
                "El comentario solicitado no existe."));

    when(servicio.cerrarHilo(eq(HILO_ID), any()))
        .thenThrow(
            new NegocioException(
                HttpStatus.NOT_FOUND,
                "hilo-no-encontrado",
                "Hilo no encontrado",
                "El hilo solicitado no existe o no pertenece al foro permanente."));

    mvc.perform(
            patch("/api/v1/comentarios/" + COMENTARIO_ID + "/ocultamiento")
                .with(csrf())
                .with(user(USUARIO_STR).roles("CATEDRATICO"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motivo": "Falta de respeto"}
                    """))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type")
                .value("https://sgp.cunoc.usac.edu.gt/errores/comentario-no-encontrado"));

    mvc.perform(
            patch("/api/v1/hilos/" + HILO_ID + "/cierre")
                .with(csrf())
                .with(user(USUARIO_STR).roles("ADMINISTRADOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motivo": "Fuera de tema"}
                    """))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type").value("https://sgp.cunoc.usac.edu.gt/errores/hilo-no-encontrado"));
  }

  @Test
  void moderacionConUsuarioNoUuidDevuelve400() throws Exception {
    mvc.perform(
            patch("/api/v1/comentarios/" + COMENTARIO_STR + "/ocultamiento")
                .with(csrf())
                .with(user("moderador-no-uuid").roles("CATEDRATICO"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motivo": "Falta de respeto"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type")
                .value("https://sgp.cunoc.usac.edu.gt/errores/identificador-usuario-invalido"));

    mvc.perform(
            patch("/api/v1/hilos/" + HILO_STR + "/cierre")
                .with(csrf())
                .with(user("moderador-no-uuid").roles("ADMINISTRADOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motivo": "Fuera de tema"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type")
                .value("https://sgp.cunoc.usac.edu.gt/errores/identificador-usuario-invalido"));
  }

  @Test
  void listarHilosConCategoriaYValidacionDeSesionEnControlador() throws Exception {
    when(servicio.listarHilos(eq(PUENTE_ID), anyInt(), anyInt(), any()))
        .thenReturn(new PaginaHilosDTO(List.of(), 0, 20, 0, 0));

    mvc.perform(get("/api/v1/puentes/" + PUENTE_STR + "/hilos").param("categoria", "consulta"))
        .andExpect(status().isOk());

    mvc.perform(get("/api/v1/puentes/" + PUENTE_STR + "/hilos").param("categoria", "   "))
        .andExpect(status().isOk());

    mvc.perform(get("/api/v1/puentes/" + PUENTE_STR + "/hilos").param("categoria", "invalida"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type").value("https://sgp.cunoc.usac.edu.gt/errores/categoria-invalida"));

    ForoController controlador = new ForoController(servicio);
    assertThatThrownBy(() -> controlador.eliminarComentario(COMENTARIO_ID, null))
        .isInstanceOf(NegocioException.class);
    assertThatThrownBy(() -> controlador.eliminarComentario(COMENTARIO_ID, () -> null))
        .isInstanceOf(NegocioException.class);
  }

  @Test
  void endpointsDeForoDeInspeccionPublicada() throws Exception {
    UUID inspeccionId = UUID.fromString("0192f5a0-0000-7000-8000-000000000050");
    HiloDTO hiloInspeccion =
        new HiloDTO(
            HILO_ID,
            PUENTE_ID,
            "Inspección publicada (2026-10-12)",
            CategoriaHilo.DISCUSION_TECNICA,
            null,
            Instant.parse("2026-10-12T12:00:00Z"),
            false,
            null);
    when(servicio.obtenerHiloInspeccion(inspeccionId)).thenReturn(hiloInspeccion);

    ComentarioDTO comentarioSinElemento =
        new ComentarioDTO(
            COMENTARIO_ID,
            HILO_ID,
            USUARIO_ID,
            "Observación general",
            Instant.parse("2026-10-12T12:05:00Z"),
            null,
            false,
            "Ana");
    ComentarioDTO comentarioAnclado =
        new ComentarioDTO(
            COMENTARIO_ID,
            HILO_ID,
            USUARIO_ID,
            "Fisura en losa",
            Instant.parse("2026-10-12T12:05:00Z"),
            null,
            false,
            "Ana",
            "superestructura.losa");
    when(servicio.listarComentariosInspeccion(eq(inspeccionId), any(), anyInt(), anyInt()))
        .thenReturn(
            new PaginaComentariosDTO(
                List.of(comentarioSinElemento, comentarioAnclado), 0, 50, 2, 1));
    when(servicio.crearComentarioInspeccion(
            eq(inspeccionId), eq(USUARIO_ID), any(CrearComentarioDTO.class)))
        .thenReturn(comentarioAnclado);

    mvc.perform(get("/api/v1/inspecciones/" + inspeccionId + "/hilo"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(HILO_STR))
        .andExpect(jsonPath("$.categoria").value("discusion_tecnica"));

    mvc.perform(
            get("/api/v1/inspecciones/" + inspeccionId + "/comentarios")
                .param("elemento_ref", "superestructura.losa"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido[1].elemento_ref").value("superestructura.losa"));

    mvc.perform(
            post("/api/v1/inspecciones/" + inspeccionId + "/comentarios")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"texto": "Fisura en losa", "elemento_ref": "superestructura.losa"}
                    """))
        .andExpect(status().isUnauthorized());

    mvc.perform(
            post("/api/v1/inspecciones/" + inspeccionId + "/comentarios")
                .with(csrf())
                .with(user(USUARIO_STR).roles("ESTUDIANTE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"texto": "Fisura en losa", "elemento_ref": "superestructura.losa"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.elemento_ref").value("superestructura.losa"));
  }
}
