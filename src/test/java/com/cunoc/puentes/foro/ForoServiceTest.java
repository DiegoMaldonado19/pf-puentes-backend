package com.cunoc.puentes.foro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cunoc.puentes.common.EntidadBase;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.foro.dto.ComentarioDTO;
import com.cunoc.puentes.foro.dto.CrearComentarioDTO;
import com.cunoc.puentes.foro.dto.CrearHiloDTO;
import com.cunoc.puentes.foro.dto.EditarComentarioDTO;
import com.cunoc.puentes.foro.dto.HiloDTO;
import com.cunoc.puentes.foro.dto.PaginaComentariosDTO;
import com.cunoc.puentes.foro.dto.PaginaHilosDTO;
import com.cunoc.puentes.foro.dto.PaginaPuentesForoDTO;
import com.cunoc.puentes.foro.dto.ResultadoModeracion;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ForoServiceTest {

  private static final UUID PUENTE_ID = UUID.fromString("0192f5a0-0000-7000-8000-0000000000a1");
  private static final UUID HILO_ID = UUID.fromString("0192f5a0-0000-7000-8000-0000000000b1");
  private static final UUID AUTOR_ID = UUID.fromString("0192f5a0-0000-7000-8000-0000000000c1");
  private static final UUID OTRO_AUTOR_ID = UUID.fromString("0192f5a0-0000-7000-8000-0000000000c2");
  private static final UUID COMENTARIO_ID = UUID.fromString("0192f5a0-0000-7000-8000-0000000000d1");

  @Mock HiloRepository hiloRepositorio;
  @Mock ComentarioRepository comentarioRepositorio;
  @Mock ForoMapper mapper;
  @Mock EntityManager entityManager;

  private ForoService servicio;

  @BeforeEach
  void setUp() {
    servicio = new ForoService(hiloRepositorio, comentarioRepositorio, mapper, entityManager);
  }

  private static void setId(EntidadBase entidad, UUID id) {
    ReflectionTestUtils.setField(entidad, "id", id);
  }

  private static void setCreadoEn(EntidadBase entidad, Instant creadoEn) {
    ReflectionTestUtils.setField(entidad, "creadoEn", creadoEn);
  }

  @Test
  void listarHilos_puenteNoExiste_lanza404() {
    when(hiloRepositorio.existePuente(PUENTE_ID)).thenReturn(false);

    assertThatThrownBy(() -> servicio.listarHilos(PUENTE_ID, 0, 20, null))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex -> {
              NegocioException ne = (NegocioException) ex;
              assertThat(ne.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(ne.getBody().getDetail()).contains("puente especificado no existe");
            });
  }

  @Test
  void listarHilos_paginacionInvalida_lanza400() {
    assertThatThrownBy(() -> servicio.listarHilos(PUENTE_ID, -1, 20, null))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> servicio.listarHilos(PUENTE_ID, 0, 0, null))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> servicio.listarHilos(PUENTE_ID, 0, 101, null))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void listarHilos_offsetDesbordado_lanza400() {
    assertThatThrownBy(() -> servicio.listarHilos(PUENTE_ID, 214748364, 100, null))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex -> {
              NegocioException ne = (NegocioException) ex;
              assertThat(ne.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(ne.getBody().getDetail()).contains("desplazamiento solicitado supera");
            });
  }

  @Test
  void listarHilos_exitoso_retornaDTO() {
    when(hiloRepositorio.existePuente(PUENTE_ID)).thenReturn(true);
    Hilo hilo = new Hilo();
    when(hiloRepositorio.findByPuenteIdAndInspeccionIdIsNull(eq(PUENTE_ID), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(hilo)));
    HiloDTO dto =
        new HiloDTO(
            HILO_ID, PUENTE_ID, "Título", CategoriaHilo.CONSULTA, AUTOR_ID, Instant.now(), false);
    when(mapper.aDTO(hilo)).thenReturn(dto);

    PaginaHilosDTO resultado = servicio.listarHilos(PUENTE_ID, 0, 20, null);

    assertThat(resultado.contenido()).containsExactly(dto);
    assertThat(resultado.totalElementos()).isEqualTo(1);
  }

  @Test
  void crearHilo_tituloInvalido_lanza400() {
    assertThatThrownBy(
            () ->
                servicio.crearHilo(
                    PUENTE_ID, AUTOR_ID, new CrearHiloDTO("", CategoriaHilo.CONSULTA)))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(
            () ->
                servicio.crearHilo(
                    PUENTE_ID, AUTOR_ID, new CrearHiloDTO("a".repeat(201), CategoriaHilo.CONSULTA)))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void crearHilo_categoriaNula_lanza400() {
    assertThatThrownBy(
            () -> servicio.crearHilo(PUENTE_ID, AUTOR_ID, new CrearHiloDTO("Título", null)))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void crearHilo_puenteNoExiste_lanza404() {
    when(hiloRepositorio.existePuente(PUENTE_ID)).thenReturn(false);

    assertThatThrownBy(
            () ->
                servicio.crearHilo(
                    PUENTE_ID, AUTOR_ID, new CrearHiloDTO("Título", CategoriaHilo.CONSULTA)))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void crearHilo_autorNoExiste_lanza400() {
    when(hiloRepositorio.existePuente(PUENTE_ID)).thenReturn(true);
    when(hiloRepositorio.existeUsuario(AUTOR_ID)).thenReturn(false);

    assertThatThrownBy(
            () ->
                servicio.crearHilo(
                    PUENTE_ID, AUTOR_ID, new CrearHiloDTO("Título", CategoriaHilo.CONSULTA)))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void crearHilo_exitoso_guardaYRetornaDTO() {
    when(hiloRepositorio.existePuente(PUENTE_ID)).thenReturn(true);
    when(hiloRepositorio.existeUsuario(AUTOR_ID)).thenReturn(true);
    Hilo guardado = new Hilo();
    when(hiloRepositorio.saveAndFlush(any(Hilo.class))).thenReturn(guardado);
    HiloDTO dto =
        new HiloDTO(
            HILO_ID, PUENTE_ID, "Título", CategoriaHilo.CONSULTA, AUTOR_ID, Instant.now(), false);
    when(mapper.aDTO(guardado)).thenReturn(dto);

    HiloDTO resultado =
        servicio.crearHilo(PUENTE_ID, AUTOR_ID, new CrearHiloDTO("Título", CategoriaHilo.CONSULTA));

    assertThat(resultado).isEqualTo(dto);
    verify(hiloRepositorio).saveAndFlush(any(Hilo.class));
    verify(entityManager).refresh(guardado);
  }

  @Test
  void obtenerHilo_inexistente_lanza404() {
    when(hiloRepositorio.findByIdAndInspeccionIdIsNull(HILO_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> servicio.obtenerHilo(HILO_ID))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void obtenerHilo_existente_retornaDTO() {
    Hilo hilo = new Hilo();
    when(hiloRepositorio.findByIdAndInspeccionIdIsNull(HILO_ID)).thenReturn(Optional.of(hilo));
    HiloDTO dto =
        new HiloDTO(
            HILO_ID, PUENTE_ID, "Título", CategoriaHilo.CONSULTA, AUTOR_ID, Instant.now(), false);
    when(mapper.aDTO(hilo)).thenReturn(dto);

    assertThat(servicio.obtenerHilo(HILO_ID)).isEqualTo(dto);
  }

  @Test
  void listarComentarios_hiloInexistente_lanza404() {
    when(hiloRepositorio.existsByIdAndInspeccionIdIsNull(HILO_ID)).thenReturn(false);

    assertThatThrownBy(() -> servicio.listarComentarios(HILO_ID, 0, 50))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void listarComentarios_exitoso_retornaPaginado() {
    when(hiloRepositorio.existsByIdAndInspeccionIdIsNull(HILO_ID)).thenReturn(true);
    Comentario c = new Comentario();
    when(comentarioRepositorio.listarRaizVisibles(eq(HILO_ID), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(c)));
    ComentarioDTO dto =
        new ComentarioDTO(
            UUID.randomUUID(), HILO_ID, AUTOR_ID, "Texto", Instant.now(), null, false);
    when(mapper.aDTO(c)).thenReturn(dto);

    PaginaComentariosDTO resultado = servicio.listarComentarios(HILO_ID, 0, 50);

    assertThat(resultado.contenido()).containsExactly(dto);
  }

  @Test
  void crearComentario_hiloNoExiste_lanza404() {
    when(hiloRepositorio.existeUsuario(AUTOR_ID)).thenReturn(true);
    when(hiloRepositorio.findByIdAndInspeccionIdIsNull(HILO_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                servicio.crearComentario(
                    HILO_ID, AUTOR_ID, new CrearComentarioDTO("Texto de comentario")))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void crearComentario_hiloCerrado_lanza409() {
    when(hiloRepositorio.existeUsuario(AUTOR_ID)).thenReturn(true);
    Hilo cerrado = new Hilo();
    cerrado.setCerradoEn(Instant.now());
    cerrado.setMotivoCierre("Resuelto");
    when(hiloRepositorio.findByIdAndInspeccionIdIsNull(HILO_ID)).thenReturn(Optional.of(cerrado));

    assertThatThrownBy(
            () ->
                servicio.crearComentario(
                    HILO_ID, AUTOR_ID, new CrearComentarioDTO("Texto de comentario")))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex -> {
              NegocioException ne = (NegocioException) ex;
              assertThat(ne.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(ne.getBody().getDetail()).contains("hilo cerrado");
            });
  }

  @Test
  void crearComentario_textoVacio_lanza400() {
    assertThatThrownBy(
            () -> servicio.crearComentario(HILO_ID, AUTOR_ID, new CrearComentarioDTO("  ")))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void crearComentario_exitoso_guardaYRetornaDTO() {
    when(hiloRepositorio.existeUsuario(AUTOR_ID)).thenReturn(true);
    Hilo abierto = new Hilo();
    when(hiloRepositorio.findByIdAndInspeccionIdIsNull(HILO_ID)).thenReturn(Optional.of(abierto));

    Comentario guardado = new Comentario();
    when(comentarioRepositorio.saveAndFlush(any(Comentario.class))).thenReturn(guardado);
    ComentarioDTO dto =
        new ComentarioDTO(
            UUID.randomUUID(), HILO_ID, AUTOR_ID, "Texto de comentario", Instant.now());
    when(mapper.aDTO(guardado)).thenReturn(dto);

    ComentarioDTO resultado =
        servicio.crearComentario(HILO_ID, AUTOR_ID, new CrearComentarioDTO("Texto de comentario"));

    assertThat(resultado).isEqualTo(dto);
    verify(comentarioRepositorio).saveAndFlush(any(Comentario.class));
    verify(entityManager).refresh(guardado);
  }

  // --- Pruebas Etapa 3: Respuestas (RN-FOR-04) ---

  @Test
  void crearComentario_conPadreValido_asociaRespuesta() {
    when(hiloRepositorio.existeUsuario(AUTOR_ID)).thenReturn(true);
    Hilo hilo = new Hilo();
    setId(hilo, HILO_ID);
    when(hiloRepositorio.findByIdAndInspeccionIdIsNull(HILO_ID)).thenReturn(Optional.of(hilo));

    Comentario padre = new Comentario();
    setId(padre, COMENTARIO_ID);
    padre.setHilo(hilo);
    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID)).thenReturn(Optional.of(padre));

    Comentario guardado = new Comentario();
    when(comentarioRepositorio.saveAndFlush(any(Comentario.class))).thenReturn(guardado);
    ComentarioDTO dto =
        new ComentarioDTO(
            UUID.randomUUID(), HILO_ID, AUTOR_ID, "Respuesta", Instant.now(), COMENTARIO_ID, false);
    when(mapper.aDTO(guardado)).thenReturn(dto);

    ComentarioDTO resultado =
        servicio.crearComentario(
            HILO_ID, AUTOR_ID, new CrearComentarioDTO("Respuesta", COMENTARIO_ID));

    assertThat(resultado.padreId()).isEqualTo(COMENTARIO_ID);
    ArgumentCaptor<Comentario> captor = ArgumentCaptor.forClass(Comentario.class);
    verify(comentarioRepositorio).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getRespuestaA()).isEqualTo(padre);
  }

  @Test
  void crearComentario_conPadreInexistente_lanza404() {
    when(hiloRepositorio.existeUsuario(AUTOR_ID)).thenReturn(true);
    Hilo hilo = new Hilo();
    setId(hilo, HILO_ID);
    when(hiloRepositorio.findByIdAndInspeccionIdIsNull(HILO_ID)).thenReturn(Optional.of(hilo));
    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                servicio.crearComentario(
                    HILO_ID, AUTOR_ID, new CrearComentarioDTO("Respuesta", COMENTARIO_ID)))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void crearComentario_conPadreDeOtroHilo_lanza404() {
    when(hiloRepositorio.existeUsuario(AUTOR_ID)).thenReturn(true);
    Hilo hiloActual = new Hilo();
    setId(hiloActual, HILO_ID);
    when(hiloRepositorio.findByIdAndInspeccionIdIsNull(HILO_ID))
        .thenReturn(Optional.of(hiloActual));

    Hilo otroHilo = new Hilo();
    setId(otroHilo, UUID.randomUUID());

    Comentario padre = new Comentario();
    setId(padre, COMENTARIO_ID);
    padre.setHilo(otroHilo);
    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID)).thenReturn(Optional.of(padre));

    assertThatThrownBy(
            () ->
                servicio.crearComentario(
                    HILO_ID, AUTOR_ID, new CrearComentarioDTO("Respuesta", COMENTARIO_ID)))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void crearComentario_conPadreEliminado_lanza404() {
    when(hiloRepositorio.existeUsuario(AUTOR_ID)).thenReturn(true);
    Hilo hilo = new Hilo();
    setId(hilo, HILO_ID);
    when(hiloRepositorio.findByIdAndInspeccionIdIsNull(HILO_ID)).thenReturn(Optional.of(hilo));

    Comentario padre = new Comentario();
    setId(padre, COMENTARIO_ID);
    padre.setHilo(hilo);
    padre.setEliminadoEn(Instant.now());
    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID)).thenReturn(Optional.of(padre));

    assertThatThrownBy(
            () ->
                servicio.crearComentario(
                    HILO_ID, AUTOR_ID, new CrearComentarioDTO("Respuesta", COMENTARIO_ID)))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void crearComentario_conPadreQueYaEsRespuesta_lanza409() {
    when(hiloRepositorio.existeUsuario(AUTOR_ID)).thenReturn(true);
    Hilo hilo = new Hilo();
    setId(hilo, HILO_ID);
    when(hiloRepositorio.findByIdAndInspeccionIdIsNull(HILO_ID)).thenReturn(Optional.of(hilo));

    Comentario abuelo = new Comentario();
    Comentario padre = new Comentario();
    setId(padre, COMENTARIO_ID);
    padre.setHilo(hilo);
    padre.setRespuestaA(abuelo);
    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID)).thenReturn(Optional.of(padre));

    assertThatThrownBy(
            () ->
                servicio.crearComentario(
                    HILO_ID,
                    AUTOR_ID,
                    new CrearComentarioDTO("Respuesta de 2do nivel", COMENTARIO_ID)))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex -> {
              NegocioException ne = (NegocioException) ex;
              assertThat(ne.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(ne.getBody().getDetail()).contains("un nivel de anidamiento");
            });
  }

  @Test
  void listarRespuestas_comentarioQueEsRespuesta_lanza404() {
    Comentario respuesta = new Comentario();
    Hilo hilo = new Hilo();
    respuesta.setHilo(hilo);
    respuesta.setRespuestaA(new Comentario());
    when(comentarioRepositorio.findById(COMENTARIO_ID)).thenReturn(Optional.of(respuesta));

    assertThatThrownBy(() -> servicio.listarRespuestas(COMENTARIO_ID, 0, 20))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void listarRespuestas_comentarioRaizEliminado_retornaRespuestas() {
    Comentario raizEliminada = new Comentario();
    Hilo hilo = new Hilo();
    raizEliminada.setHilo(hilo);
    raizEliminada.setEliminadoEn(Instant.now());
    when(comentarioRepositorio.findById(COMENTARIO_ID)).thenReturn(Optional.of(raizEliminada));

    Comentario resp = new Comentario();
    when(comentarioRepositorio.listarRespuestasVisibles(eq(COMENTARIO_ID), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(resp)));
    ComentarioDTO dto =
        new ComentarioDTO(
            UUID.randomUUID(), HILO_ID, AUTOR_ID, "Texto", Instant.now(), COMENTARIO_ID, false);
    when(mapper.aDTO(resp)).thenReturn(dto);

    PaginaComentariosDTO resultado = servicio.listarRespuestas(COMENTARIO_ID, 0, 20);

    assertThat(resultado.contenido()).containsExactly(dto);
  }

  // --- Pruebas Etapa 3: Edición 15 min con reloj controlable (RN-FOR-05) ---

  @Test
  void editarComentario_dentroDe15Minutos_actualizaTexto() {
    Instant creadoEn = Instant.parse("2026-10-12T10:00:00Z");
    Instant aLos10Min = Instant.parse("2026-10-12T10:10:00Z");
    Clock relojControlable = Clock.fixed(aLos10Min, ZoneOffset.UTC);

    ForoService servicioConReloj =
        new ForoService(
            hiloRepositorio, comentarioRepositorio, mapper, entityManager, relojControlable);

    Comentario comentario = new Comentario();
    setId(comentario, COMENTARIO_ID);
    comentario.setAutorId(AUTOR_ID);
    setCreadoEn(comentario, creadoEn);
    Hilo hilo = new Hilo();
    comentario.setHilo(hilo);

    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(comentario));
    when(comentarioRepositorio.saveAndFlush(any(Comentario.class))).thenReturn(comentario);
    ComentarioDTO dto =
        new ComentarioDTO(COMENTARIO_ID, HILO_ID, AUTOR_ID, "Texto editado", creadoEn);
    when(mapper.aDTO(comentario)).thenReturn(dto);

    ComentarioDTO resultado =
        servicioConReloj.editarComentario(
            COMENTARIO_ID, AUTOR_ID, new EditarComentarioDTO("Texto editado"));

    assertThat(resultado).isEqualTo(dto);
    assertThat(comentario.getTexto()).isEqualTo("Texto editado");
    assertThat(comentario.getEditadoEn()).isEqualTo(aLos10Min.truncatedTo(ChronoUnit.MICROS));
  }

  @Test
  void editarComentario_exactamenteEn15Minutos_lanza409() {
    Instant creadoEn = Instant.parse("2026-10-12T10:00:00Z");
    Instant aLos15MinExactos = Instant.parse("2026-10-12T10:15:00Z");
    Clock relojControlable = Clock.fixed(aLos15MinExactos, ZoneOffset.UTC);

    ForoService servicioConReloj =
        new ForoService(
            hiloRepositorio, comentarioRepositorio, mapper, entityManager, relojControlable);

    Comentario comentario = new Comentario();
    setId(comentario, COMENTARIO_ID);
    comentario.setAutorId(AUTOR_ID);
    setCreadoEn(comentario, creadoEn);
    Hilo hilo = new Hilo();
    comentario.setHilo(hilo);

    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(comentario));

    assertThatThrownBy(
            () ->
                servicioConReloj.editarComentario(
                    COMENTARIO_ID, AUTOR_ID, new EditarComentarioDTO("Texto editado")))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex -> {
              NegocioException ne = (NegocioException) ex;
              assertThat(ne.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(ne.getBody().getDetail()).contains("15 minutos");
            });
  }

  @Test
  void editarComentario_otroAutor_lanza404() {
    Comentario comentario = new Comentario();
    setId(comentario, COMENTARIO_ID);
    comentario.setAutorId(AUTOR_ID);
    Hilo hilo = new Hilo();
    comentario.setHilo(hilo);

    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(comentario));

    assertThatThrownBy(
            () ->
                servicio.editarComentario(
                    COMENTARIO_ID, OTRO_AUTOR_ID, new EditarComentarioDTO("Intento ajeno")))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void editarComentario_eliminado_lanza404() {
    Comentario comentario = new Comentario();
    setId(comentario, COMENTARIO_ID);
    comentario.setAutorId(AUTOR_ID);
    comentario.setEliminadoEn(Instant.now());
    Hilo hilo = new Hilo();
    comentario.setHilo(hilo);

    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(comentario));

    assertThatThrownBy(
            () ->
                servicio.editarComentario(
                    COMENTARIO_ID, AUTOR_ID, new EditarComentarioDTO("Intento en eliminado")))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
  }

  // --- Pruebas Etapa 3: Borrado lógico idempotente (RN-FOR-05) ---

  @Test
  void eliminarComentario_autorCorrecto_asignaEliminadoEn() {
    Comentario comentario = new Comentario();
    setId(comentario, COMENTARIO_ID);
    comentario.setAutorId(AUTOR_ID);
    Hilo hilo = new Hilo();
    comentario.setHilo(hilo);

    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(comentario));

    servicio.eliminarComentario(COMENTARIO_ID, AUTOR_ID);

    assertThat(comentario.getEliminadoEn()).isNotNull();
    verify(comentarioRepositorio).saveAndFlush(comentario);
  }

  @Test
  void eliminarComentario_otroAutor_lanza404() {
    Comentario comentario = new Comentario();
    setId(comentario, COMENTARIO_ID);
    comentario.setAutorId(AUTOR_ID);
    Hilo hilo = new Hilo();
    comentario.setHilo(hilo);

    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(comentario));

    assertThatThrownBy(() -> servicio.eliminarComentario(COMENTARIO_ID, OTRO_AUTOR_ID))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));

    verify(comentarioRepositorio, never()).saveAndFlush(any());
  }

  @Test
  void eliminarComentario_idempotente_segundoLlamadoNoFalla() {
    Comentario comentario = new Comentario();
    setId(comentario, COMENTARIO_ID);
    comentario.setAutorId(AUTOR_ID);
    comentario.setEliminadoEn(Instant.now());
    Hilo hilo = new Hilo();
    comentario.setHilo(hilo);

    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(comentario));

    servicio.eliminarComentario(COMENTARIO_ID, AUTOR_ID);

    verify(comentarioRepositorio, never()).saveAndFlush(any());
  }

  // --- Pruebas Etapa 4: Moderación de comentarios e hilos ---

  @Test
  void ocultarComentario_comentarioInexistente_lanza404() {
    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> servicio.ocultarComentario(COMENTARIO_ID, "Spam"))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));

    verify(comentarioRepositorio, never()).saveAndFlush(any());
  }

  @Test
  void ocultarComentario_hiloConInspeccion_lanza404() {
    Comentario comentario = new Comentario();
    setId(comentario, COMENTARIO_ID);
    Hilo hilo = new Hilo();
    hilo.setInspeccionId(UUID.randomUUID());
    comentario.setHilo(hilo);

    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(comentario));

    assertThatThrownBy(() -> servicio.ocultarComentario(COMENTARIO_ID, "Spam"))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));

    verify(comentarioRepositorio, never()).saveAndFlush(any());
  }

  @Test
  void ocultarComentario_motivoVacio_lanza400() {
    assertThatThrownBy(() -> servicio.ocultarComentario(COMENTARIO_ID, "   "))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> servicio.ocultarComentario(COMENTARIO_ID, null))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void ocultarComentario_yaOculto_idempotenteNoGuarda() {
    Comentario comentario = new Comentario();
    setId(comentario, COMENTARIO_ID);
    comentario.setOcultoEn(Instant.now());
    comentario.setMotivoOcultamiento("Motivo previo");
    Hilo hilo = new Hilo();
    comentario.setHilo(hilo);

    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(comentario));

    ResultadoModeracion resultado = servicio.ocultarComentario(COMENTARIO_ID, "Nuevo motivo");

    assertThat(resultado.modificado()).isFalse();
    assertThat(comentario.getMotivoOcultamiento()).isEqualTo("Motivo previo");
    verify(comentarioRepositorio, never()).saveAndFlush(any());
  }

  @Test
  void ocultarComentario_raizConRespuestas_cascadaVisiblePreservaOcultas() {
    Comentario raiz = new Comentario();
    setId(raiz, COMENTARIO_ID);
    Hilo hilo = new Hilo();
    setId(hilo, HILO_ID);
    raiz.setHilo(hilo);

    UUID respVisibleId = UUID.randomUUID();
    Comentario respVisible = new Comentario();
    setId(respVisible, respVisibleId);
    respVisible.setHilo(hilo);
    respVisible.setRespuestaA(raiz);

    UUID respYaOcultaId = UUID.randomUUID();
    Comentario respYaOculta = new Comentario();
    setId(respYaOculta, respYaOcultaId);
    respYaOculta.setHilo(hilo);
    respYaOculta.setRespuestaA(raiz);
    Instant fechaPrevia = Instant.now().minus(2, ChronoUnit.HOURS);
    respYaOculta.setOcultoEn(fechaPrevia);
    respYaOculta.setMotivoOcultamiento("Previo");

    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID)).thenReturn(Optional.of(raiz));
    when(comentarioRepositorio.buscarRespuestasDirectasParaModificar(COMENTARIO_ID, HILO_ID))
        .thenReturn(List.of(respVisible, respYaOculta));

    ResultadoModeracion resultado = servicio.ocultarComentario(COMENTARIO_ID, "Contenido ofensivo");

    assertThat(resultado.modificado()).isTrue();
    assertThat(resultado.idsAfectados()).containsExactly(COMENTARIO_ID, respVisibleId);
    assertThat(raiz.getOcultoEn()).isNotNull();
    assertThat(raiz.getMotivoOcultamiento()).isEqualTo("Contenido ofensivo");
    assertThat(respVisible.getOcultoEn()).isEqualTo(raiz.getOcultoEn());
    assertThat(respVisible.getMotivoOcultamiento()).isEqualTo("Contenido ofensivo");
    assertThat(respYaOculta.getOcultoEn()).isEqualTo(fechaPrevia);
    assertThat(respYaOculta.getMotivoOcultamiento()).isEqualTo("Previo");

    verify(comentarioRepositorio).saveAndFlush(raiz);
    verify(comentarioRepositorio).saveAllAndFlush(List.of(respVisible, respYaOculta));
  }

  @Test
  void ocultarComentario_respuesta_soloOcultaSiMisma() {
    Comentario raiz = new Comentario();
    setId(raiz, UUID.randomUUID());
    Hilo hilo = new Hilo();
    raiz.setHilo(hilo);

    Comentario respuesta = new Comentario();
    setId(respuesta, COMENTARIO_ID);
    respuesta.setHilo(hilo);
    respuesta.setRespuestaA(raiz);

    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(respuesta));

    ResultadoModeracion resultado = servicio.ocultarComentario(COMENTARIO_ID, "Spam en respuesta");

    assertThat(resultado.modificado()).isTrue();
    assertThat(resultado.idsAfectados()).containsExactly(COMENTARIO_ID);
    assertThat(respuesta.getOcultoEn()).isNotNull();
    verify(comentarioRepositorio, never()).buscarRespuestasDirectasParaModificar(any(), any());
    verify(comentarioRepositorio).saveAndFlush(respuesta);
  }

  @Test
  void ocultarComentario_marcaEliminado_permiteOcultar() {
    Comentario eliminado = new Comentario();
    setId(eliminado, COMENTARIO_ID);
    Hilo hilo = new Hilo();
    eliminado.setHilo(hilo);
    eliminado.setEliminadoEn(Instant.now());

    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(eliminado));

    ResultadoModeracion resultado = servicio.ocultarComentario(COMENTARIO_ID, "Ocultar marca");

    assertThat(resultado.modificado()).isTrue();
    assertThat(eliminado.getOcultoEn()).isNotNull();
    verify(comentarioRepositorio).saveAndFlush(eliminado);
  }

  @Test
  void cerrarHilo_inexistente_lanza404() {
    when(hiloRepositorio.buscarParaModificar(HILO_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> servicio.cerrarHilo(HILO_ID, "Cierre"))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));

    verify(hiloRepositorio, never()).saveAndFlush(any());
  }

  @Test
  void cerrarHilo_motivoVacio_lanza400() {
    assertThatThrownBy(() -> servicio.cerrarHilo(HILO_ID, "   "))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void cerrarHilo_yaCerrado_idempotenteNoGuarda() {
    Hilo hilo = new Hilo();
    setId(hilo, HILO_ID);
    hilo.setCerradoEn(Instant.now());
    hilo.setMotivoCierre("Cierre original");

    when(hiloRepositorio.buscarParaModificar(HILO_ID)).thenReturn(Optional.of(hilo));

    ResultadoModeracion resultado = servicio.cerrarHilo(HILO_ID, "Nuevo motivo");

    assertThat(resultado.modificado()).isFalse();
    assertThat(hilo.getMotivoCierre()).isEqualTo("Cierre original");
    verify(hiloRepositorio, never()).saveAndFlush(any());
  }

  @Test
  void cerrarHilo_abierto_asignaCerradoEnYMotivo() {
    Hilo hilo = new Hilo();
    setId(hilo, HILO_ID);

    when(hiloRepositorio.buscarParaModificar(HILO_ID)).thenReturn(Optional.of(hilo));

    ResultadoModeracion resultado = servicio.cerrarHilo(HILO_ID, "Tema concluido");

    assertThat(resultado.modificado()).isTrue();
    assertThat(resultado.idsAfectados()).containsExactly(HILO_ID);
    assertThat(hilo.getCerradoEn()).isNotNull();
    assertThat(hilo.getMotivoCierre()).isEqualTo("Tema concluido");
    verify(hiloRepositorio).saveAndFlush(hilo);
  }

  @Test
  void listarPuentesForo_conTerminoBusqueda_normalizaYRetornaPagina() {
    HiloRepository.PuenteResumen resumen =
        new HiloRepository.PuenteResumen() {
          @Override
          public UUID getId() {
            return PUENTE_ID;
          }

          @Override
          public String getCodigo() {
            return "GT-09-0901-0001";
          }

          @Override
          public String getNombre() {
            return "Puente Los Chocoyos";
          }

          @Override
          public String getRuta() {
            return "CA-1 Occidente";
          }

          @Override
          public String getMunicipio() {
            return "Quetzaltenango";
          }

          @Override
          public String getDepartamento() {
            return "Quetzaltenango";
          }

          @Override
          public String getEstadoActual() {
            return "BUENO";
          }

          @Override
          public long getCantidadHilos() {
            return 3L;
          }
        };

    when(hiloRepositorio.listarPuentesForo(eq("Chocoyos"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(resumen)));

    PaginaPuentesForoDTO pagina = servicio.listarForos("  Chocoyos  ", 0, 12);

    assertThat(pagina.totalElementos()).isEqualTo(1);
    assertThat(pagina.contenido()).hasSize(1);
    assertThat(pagina.contenido().get(0).id()).isEqualTo(PUENTE_ID);
    assertThat(pagina.contenido().get(0).codigo()).isEqualTo("GT-09-0901-0001");
    assertThat(pagina.contenido().get(0).nombre()).isEqualTo("Puente Los Chocoyos");
    assertThat(pagina.contenido().get(0).cantidadHilos()).isEqualTo(3L);
  }

  @Test
  void listarPuentesForo_conTerminoBlancoONulo_usaCadenaVacia() {
    when(hiloRepositorio.listarPuentesForo(eq(""), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    PaginaPuentesForoDTO pagina = servicio.listarForos("   ", 0, 12);

    assertThat(pagina.contenido()).isEmpty();
    assertThat(pagina.totalElementos()).isZero();
  }

  @Test
  void enriquecerAutores_incluyeNombreAutorOMantieneNuloParaSistema() {
    Instant ahora = Instant.now();
    Hilo hiloConAutor = new Hilo();
    setId(hiloConAutor, HILO_ID);
    hiloConAutor.setAutorId(AUTOR_ID);

    HiloDTO dtoBase =
        new HiloDTO(
            HILO_ID, PUENTE_ID, "Hilo con autor", CategoriaHilo.CONSULTA, AUTOR_ID, ahora, false);

    HiloRepository.Autor autorResumen =
        new HiloRepository.Autor() {
          @Override
          public UUID getId() {
            return AUTOR_ID;
          }

          @Override
          public String getNombre() {
            return "María José";
          }
        };

    when(hiloRepositorio.findByIdAndInspeccionIdIsNull(HILO_ID))
        .thenReturn(Optional.of(hiloConAutor));
    when(mapper.aDTO(hiloConAutor)).thenReturn(dtoBase);
    when(hiloRepositorio.buscarNombresAutores(any())).thenReturn(List.of(autorResumen));

    HiloDTO enriquecido = servicio.obtenerHilo(HILO_ID);
    assertThat(enriquecido.autorNombre()).isEqualTo("María José");

    Hilo hiloSistema = new Hilo();
    setId(hiloSistema, HILO_ID);
    hiloSistema.setAutorId(null);
    HiloDTO dtoSistema =
        new HiloDTO(
            HILO_ID, PUENTE_ID, "Hilo automático", CategoriaHilo.ALERTA, null, ahora, false);
    when(hiloRepositorio.findByIdAndInspeccionIdIsNull(HILO_ID))
        .thenReturn(Optional.of(hiloSistema));
    when(mapper.aDTO(hiloSistema)).thenReturn(dtoSistema);

    HiloDTO resultadoSistema = servicio.obtenerHilo(HILO_ID);
    assertThat(resultadoSistema.autorId()).isNull();
    assertThat(resultadoSistema.autorNombre()).isNull();
  }

  @Test
  void foroMapper_transformaHiloYComentarioCorrectamente() {
    ForoMapper mapperReal = Mappers.getMapper(ForoMapper.class);
    Instant fechaConNanos = Instant.parse("2026-10-10T12:00:00.123456789Z");

    Hilo hilo = new Hilo();
    setId(hilo, HILO_ID);
    setCreadoEn(hilo, fechaConNanos);
    hilo.setPuenteId(PUENTE_ID);
    hilo.setAutorId(AUTOR_ID);
    hilo.setTitulo("Título");
    hilo.setCategoria(CategoriaHilo.ALERTA);
    hilo.setCerradoEn(fechaConNanos);

    HiloDTO hiloDto = mapperReal.aDTO(hilo);
    assertThat(hiloDto.id()).isEqualTo(HILO_ID);
    assertThat(hiloDto.cerrado()).isTrue();
    assertThat(hiloDto.creadoEn()).isEqualTo(Instant.parse("2026-10-10T12:00:00.123456Z"));

    Comentario activo = new Comentario();
    setId(activo, COMENTARIO_ID);
    setCreadoEn(activo, fechaConNanos);
    activo.setHilo(hilo);
    activo.setAutorId(AUTOR_ID);
    activo.setTexto("Texto visible");

    ComentarioDTO activoDto = mapperReal.aDTO(activo);
    assertThat(activoDto.hiloId()).isEqualTo(HILO_ID);
    assertThat(activoDto.eliminado()).isFalse();
    assertThat(activoDto.texto()).isEqualTo("Texto visible");

    Comentario eliminado = new Comentario();
    setId(eliminado, UUID.randomUUID());
    setCreadoEn(eliminado, fechaConNanos);
    eliminado.setHilo(hilo);
    eliminado.setRespuestaA(activo);
    eliminado.setAutorId(AUTOR_ID);
    eliminado.setTexto("Texto borrado");
    eliminado.setEliminadoEn(fechaConNanos);

    ComentarioDTO eliminadoDto = mapperReal.aDTO(eliminado);
    assertThat(eliminadoDto.padreId()).isEqualTo(COMENTARIO_ID);
    assertThat(eliminadoDto.eliminado()).isTrue();
    assertThat(eliminadoDto.texto()).isNull();
  }

  @Test
  void categoriaHiloYRelojForo_funcionanSegunContrato() {
    assertThat(CategoriaHilo.desdeCodigo(null)).isNull();
    assertThat(CategoriaHilo.desdeCodigo("  consulta ")).isEqualTo(CategoriaHilo.CONSULTA);
    assertThat(CategoriaHilo.desdeCodigo("DISCUSION_TECNICA"))
        .isEqualTo(CategoriaHilo.DISCUSION_TECNICA);
    assertThat(CategoriaHilo.ALERTA.getCodigo()).isEqualTo("alerta");
    assertThatThrownBy(() -> CategoriaHilo.desdeCodigo("inexistente"))
        .isInstanceOf(IllegalArgumentException.class);

    ForoClockConfig config = new ForoClockConfig();
    assertThat(config.clock()).isNotNull();
  }

  @Test
  void resultadoModeracionYMapeosNulos_cubrenTodasLasRamas() {
    ForoMapper mapperReal = Mappers.getMapper(ForoMapper.class);
    assertThat(mapperReal.aDTO((Hilo) null)).isNull();
    assertThat(mapperReal.aDTO((Comentario) null)).isNull();
    assertThat(mapperReal.normalizarFecha(null)).isNull();

    Comentario sinHilo = new Comentario();
    setId(sinHilo, COMENTARIO_ID);
    sinHilo.setAutorId(AUTOR_ID);
    sinHilo.setTexto("Texto");
    assertThat(mapperReal.aDTO(sinHilo).hiloId()).isNull();

    Instant instante = Instant.parse("2026-10-10T12:00:00Z");
    ResultadoModeracion modOculto =
        new ResultadoModeracion(true, COMENTARIO_ID, instante, "Spam", List.of(COMENTARIO_ID));
    assertThat(modOculto.valoresAnteriores("oculto")).containsEntry("oculto", false);
    assertThat(modOculto.valoresNuevos("oculto"))
        .containsEntry("oculto", true)
        .containsEntry("oculto_en", "2026-10-10T12:00:00Z")
        .containsEntry("motivo_ocultamiento", "Spam")
        .containsEntry("ids_ocultados", List.of(COMENTARIO_ID.toString()));

    ResultadoModeracion modCerrado =
        new ResultadoModeracion(true, HILO_ID, instante, "Cerrado", List.of(HILO_ID));
    assertThat(modCerrado.valoresAnteriores("cerrado")).containsEntry("cerrado", false);
    assertThat(modCerrado.valoresNuevos("cerrado"))
        .containsEntry("cerrado", true)
        .containsEntry("cerrado_en", "2026-10-10T12:00:00Z")
        .containsEntry("motivo_cierre", "Cerrado")
        .doesNotContainKey("ids_ocultados");
  }

  @Test
  void ramasAdicionalesDeValidacionYConsultasEnForoService() {
    when(hiloRepositorio.listarPuentesForo(eq(""), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));
    assertThat(servicio.listarForos(null, 0, 12).contenido()).isEmpty();

    assertThatThrownBy(() -> servicio.listarForos("x".repeat(121), 0, 12))
        .isInstanceOf(NegocioException.class);

    when(hiloRepositorio.existePuente(PUENTE_ID)).thenReturn(true);
    when(hiloRepositorio.findByPuenteIdAndInspeccionIdIsNullAndCategoria(
            eq(PUENTE_ID), eq(CategoriaHilo.CONSULTA), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));
    assertThat(servicio.listarHilos(PUENTE_ID, 0, 20, CategoriaHilo.CONSULTA).contenido())
        .isEmpty();

    assertThatThrownBy(() -> servicio.crearHilo(PUENTE_ID, AUTOR_ID, null))
        .isInstanceOf(NegocioException.class);
    assertThatThrownBy(
            () ->
                servicio.crearHilo(
                    PUENTE_ID, AUTOR_ID, new CrearHiloDTO(null, CategoriaHilo.CONSULTA)))
        .isInstanceOf(NegocioException.class);

    assertThatThrownBy(() -> servicio.crearComentario(HILO_ID, AUTOR_ID, null))
        .isInstanceOf(NegocioException.class);
    assertThatThrownBy(
            () -> servicio.crearComentario(HILO_ID, AUTOR_ID, new CrearComentarioDTO(null)))
        .isInstanceOf(NegocioException.class);

    when(hiloRepositorio.existeUsuario(AUTOR_ID)).thenReturn(false);
    assertThatThrownBy(
            () -> servicio.crearComentario(HILO_ID, AUTOR_ID, new CrearComentarioDTO("Hola")))
        .isInstanceOf(NegocioException.class);

    when(hiloRepositorio.existeUsuario(AUTOR_ID)).thenReturn(true);
    Hilo hiloActivo = new Hilo();
    setId(hiloActivo, HILO_ID);
    when(hiloRepositorio.findByIdAndInspeccionIdIsNull(HILO_ID))
        .thenReturn(Optional.of(hiloActivo));

    Hilo hiloInspeccion = new Hilo();
    setId(hiloInspeccion, HILO_ID);
    hiloInspeccion.setInspeccionId(UUID.randomUUID());

    Comentario padreDeInspeccion = new Comentario();
    setId(padreDeInspeccion, COMENTARIO_ID);
    padreDeInspeccion.setHilo(hiloInspeccion);
    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(padreDeInspeccion));
    assertThatThrownBy(
            () ->
                servicio.crearComentario(
                    HILO_ID, AUTOR_ID, new CrearComentarioDTO("Respuesta", COMENTARIO_ID)))
        .isInstanceOf(NegocioException.class);

    Comentario padreOculto = new Comentario();
    setId(padreOculto, COMENTARIO_ID);
    padreOculto.setHilo(hiloActivo);
    padreOculto.setOcultoEn(Instant.now());
    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(padreOculto));
    assertThatThrownBy(
            () ->
                servicio.crearComentario(
                    HILO_ID, AUTOR_ID, new CrearComentarioDTO("Respuesta", COMENTARIO_ID)))
        .isInstanceOf(NegocioException.class);

    when(comentarioRepositorio.findById(COMENTARIO_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> servicio.listarRespuestas(COMENTARIO_ID, 0, 20))
        .isInstanceOf(NegocioException.class);

    when(comentarioRepositorio.findById(COMENTARIO_ID)).thenReturn(Optional.of(padreDeInspeccion));
    assertThatThrownBy(() -> servicio.listarRespuestas(COMENTARIO_ID, 0, 20))
        .isInstanceOf(NegocioException.class);

    when(comentarioRepositorio.findById(COMENTARIO_ID)).thenReturn(Optional.of(padreOculto));
    assertThatThrownBy(() -> servicio.listarRespuestas(COMENTARIO_ID, 0, 20))
        .isInstanceOf(NegocioException.class);

    assertThatThrownBy(() -> servicio.editarComentario(COMENTARIO_ID, AUTOR_ID, null))
        .isInstanceOf(NegocioException.class);
    assertThatThrownBy(
            () -> servicio.editarComentario(COMENTARIO_ID, AUTOR_ID, new EditarComentarioDTO(null)))
        .isInstanceOf(NegocioException.class);
    assertThatThrownBy(
            () -> servicio.editarComentario(COMENTARIO_ID, AUTOR_ID, new EditarComentarioDTO("  ")))
        .isInstanceOf(NegocioException.class);

    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                servicio.editarComentario(
                    COMENTARIO_ID, AUTOR_ID, new EditarComentarioDTO("Texto")))
        .isInstanceOf(NegocioException.class);
    assertThatThrownBy(() -> servicio.eliminarComentario(COMENTARIO_ID, AUTOR_ID))
        .isInstanceOf(NegocioException.class);

    padreDeInspeccion.setAutorId(AUTOR_ID);
    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(padreDeInspeccion));
    assertThatThrownBy(
            () ->
                servicio.editarComentario(
                    COMENTARIO_ID, AUTOR_ID, new EditarComentarioDTO("Texto")))
        .isInstanceOf(NegocioException.class);
    assertThatThrownBy(() -> servicio.eliminarComentario(COMENTARIO_ID, AUTOR_ID))
        .isInstanceOf(NegocioException.class);

    padreOculto.setAutorId(AUTOR_ID);
    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(padreOculto));
    assertThatThrownBy(
            () ->
                servicio.editarComentario(
                    COMENTARIO_ID, AUTOR_ID, new EditarComentarioDTO("Texto")))
        .isInstanceOf(NegocioException.class);
    assertThatThrownBy(() -> servicio.eliminarComentario(COMENTARIO_ID, AUTOR_ID))
        .isInstanceOf(NegocioException.class);

    assertThatThrownBy(() -> servicio.cerrarHilo(HILO_ID, null))
        .isInstanceOf(NegocioException.class);
  }
}
