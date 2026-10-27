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
import com.cunoc.puentes.indice.EstadoCondicion;
import com.cunoc.puentes.inspeccion.InspeccionPublicada;
import com.cunoc.puentes.notificacion.NotificacionService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ForoInspeccionPublicadaListenerTest {

  private static final UUID PUENTE_ID = UUID.fromString("0192f5a0-0000-7000-8000-0000000000a1");
  private static final UUID INSPECCION_ID = UUID.fromString("0192f5a0-0000-7000-8000-0000000000e1");
  private static final UUID HILO_ID = UUID.fromString("0192f5a0-0000-7000-8000-0000000000b1");
  private static final UUID AUTOR_ID = UUID.fromString("0192f5a0-0000-7000-8000-0000000000c1");
  private static final UUID MENCION_1 = UUID.fromString("0192f5a0-0000-7000-8000-0000000000c2");
  private static final UUID MENCION_2 = UUID.fromString("0192f5a0-0000-7000-8000-0000000000c3");
  private static final UUID COMENTARIO_ID = UUID.fromString("0192f5a0-0000-7000-8000-0000000000d1");

  @Mock HiloRepository hiloRepositorio;
  @Mock ComentarioRepository comentarioRepositorio;
  @Mock ForoMapper mapper;
  @Mock EntityManager entityManager;
  @Mock NotificacionService notificacionService;

  private Clock relojFijo;
  private ForoService servicio;
  private ForoInspeccionPublicadaListener listener;

  @BeforeEach
  void setUp() {
    relojFijo = Clock.fixed(Instant.parse("2026-10-12T10:05:00Z"), ZoneOffset.UTC);
    servicio =
        new ForoService(
            hiloRepositorio,
            comentarioRepositorio,
            mapper,
            entityManager,
            notificacionService,
            relojFijo);
    listener = new ForoInspeccionPublicadaListener(servicio);
  }

  private static void setId(EntidadBase entidad, UUID id) {
    ReflectionTestUtils.setField(entidad, "id", id);
  }

  private static void setCreadoEn(EntidadBase entidad, Instant creadoEn) {
    ReflectionTestUtils.setField(entidad, "creadoEn", creadoEn);
  }

  private static HiloRepository.InspeccionResumen resumen(UUID id, UUID puenteId, String estado) {
    return new HiloRepository.InspeccionResumen() {
      @Override
      public UUID getId() {
        return id;
      }

      @Override
      public UUID getPuenteId() {
        return puenteId;
      }

      @Override
      public String getEstado() {
        return estado;
      }
    };
  }

  @Test
  void listener_abreHiloDeInspeccionPublicada_eIdempotenteEnReintento() {
    InspeccionPublicada evento =
        new InspeccionPublicada(
            INSPECCION_ID,
            PUENTE_ID,
            new BigDecimal("3.80"),
            EstadoCondicion.BUENO,
            LocalDate.of(2026, 10, 12));

    when(hiloRepositorio.existePuente(PUENTE_ID)).thenReturn(true);
    when(hiloRepositorio.buscarInspeccion(INSPECCION_ID))
        .thenReturn(Optional.of(resumen(INSPECCION_ID, PUENTE_ID, "PUBLICADA")));
    when(hiloRepositorio.findByInspeccionId(INSPECCION_ID)).thenReturn(Optional.empty());

    Hilo guardado = new Hilo();
    setId(guardado, HILO_ID);
    guardado.setPuenteId(PUENTE_ID);
    guardado.setInspeccionId(INSPECCION_ID);
    guardado.setTitulo("Inspección publicada (2026-10-12)");
    guardado.setCategoria(CategoriaHilo.DISCUSION_TECNICA);
    when(hiloRepositorio.saveAndFlush(any(Hilo.class))).thenReturn(guardado);

    HiloDTO dto =
        new HiloDTO(
            HILO_ID,
            PUENTE_ID,
            guardado.getTitulo(),
            CategoriaHilo.DISCUSION_TECNICA,
            null,
            Instant.now(),
            false);
    when(mapper.aDTO(guardado)).thenReturn(dto);

    listener.alPublicar(evento);
    verify(hiloRepositorio).saveAndFlush(any(Hilo.class));

    when(hiloRepositorio.findByInspeccionId(INSPECCION_ID)).thenReturn(Optional.of(guardado));
    Optional<HiloDTO> repetido = servicio.abrirHiloDeInspeccionPublicada(evento);
    assertThat(repetido).contains(dto);
  }

  @Test
  void abrirHiloDeInspeccionPublicada_validacionesDeContrato() {
    LocalDate fecha = LocalDate.of(2026, 10, 12);
    BigDecimal ic = new BigDecimal("2.50");

    assertThatThrownBy(() -> servicio.abrirHiloDeInspeccionPublicada(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                servicio.abrirHiloDeInspeccionPublicada(
                    new InspeccionPublicada(null, PUENTE_ID, ic, EstadoCondicion.MALO, fecha)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                servicio.abrirHiloDeInspeccionPublicada(
                    new InspeccionPublicada(INSPECCION_ID, null, ic, EstadoCondicion.MALO, fecha)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                servicio.abrirHiloDeInspeccionPublicada(
                    new InspeccionPublicada(
                        INSPECCION_ID, PUENTE_ID, null, EstadoCondicion.MALO, fecha)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                servicio.abrirHiloDeInspeccionPublicada(
                    new InspeccionPublicada(INSPECCION_ID, PUENTE_ID, ic, null, fecha)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                servicio.abrirHiloDeInspeccionPublicada(
                    new InspeccionPublicada(
                        INSPECCION_ID, PUENTE_ID, ic, EstadoCondicion.MALO, null)))
        .isInstanceOf(IllegalArgumentException.class);

    InspeccionPublicada evento =
        new InspeccionPublicada(INSPECCION_ID, PUENTE_ID, ic, EstadoCondicion.MALO, fecha);

    when(hiloRepositorio.existePuente(PUENTE_ID)).thenReturn(false);
    assertThatThrownBy(() -> servicio.abrirHiloDeInspeccionPublicada(evento))
        .isInstanceOf(IllegalStateException.class);

    when(hiloRepositorio.existePuente(PUENTE_ID)).thenReturn(true);
    when(hiloRepositorio.buscarInspeccion(INSPECCION_ID)).thenReturn(Optional.empty());
    assertThat(servicio.abrirHiloDeInspeccionPublicada(evento)).isEmpty();

    when(hiloRepositorio.buscarInspeccion(INSPECCION_ID))
        .thenReturn(Optional.of(resumen(INSPECCION_ID, UUID.randomUUID(), "PUBLICADA")));
    assertThatThrownBy(() -> servicio.abrirHiloDeInspeccionPublicada(evento))
        .isInstanceOf(IllegalStateException.class);

    when(hiloRepositorio.buscarInspeccion(INSPECCION_ID))
        .thenReturn(Optional.of(resumen(INSPECCION_ID, PUENTE_ID, "EN_REVISION")));
    assertThatThrownBy(() -> servicio.abrirHiloDeInspeccionPublicada(evento))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void foroDeInspeccion_obtenerListarYCrearComentariosConElementoRefYMenciones() {
    when(hiloRepositorio.findByInspeccionId(INSPECCION_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> servicio.obtenerHiloInspeccion(INSPECCION_ID))
        .isInstanceOf(NegocioException.class);
    assertThatThrownBy(() -> servicio.listarComentariosInspeccion(INSPECCION_ID, null, 0, 20))
        .isInstanceOf(NegocioException.class);

    Hilo hiloInspeccion = new Hilo();
    setId(hiloInspeccion, HILO_ID);
    hiloInspeccion.setPuenteId(PUENTE_ID);
    hiloInspeccion.setInspeccionId(INSPECCION_ID);
    when(hiloRepositorio.findByInspeccionId(INSPECCION_ID)).thenReturn(Optional.of(hiloInspeccion));

    HiloDTO hiloDto =
        new HiloDTO(
            HILO_ID,
            PUENTE_ID,
            "Inspección publicada",
            CategoriaHilo.DISCUSION_TECNICA,
            null,
            Instant.now(),
            false);
    when(mapper.aDTO(hiloInspeccion)).thenReturn(hiloDto);
    assertThat(servicio.obtenerHiloInspeccion(INSPECCION_ID)).isEqualTo(hiloDto);

    Comentario cRaiz = new Comentario();
    setId(cRaiz, COMENTARIO_ID);
    cRaiz.setHilo(hiloInspeccion);
    cRaiz.setAutorId(AUTOR_ID);
    cRaiz.setElementoRef("superestructura.losa");

    ComentarioDTO cDto =
        new ComentarioDTO(
            COMENTARIO_ID,
            HILO_ID,
            AUTOR_ID,
            "Texto",
            Instant.now(),
            null,
            false,
            "Ana",
            "superestructura.losa");
    when(mapper.aDTO(cRaiz)).thenReturn(cDto);
    when(comentarioRepositorio.listarRaizInspeccionVisibles(eq(HILO_ID), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(cRaiz)));
    when(comentarioRepositorio.listarRaizInspeccionVisiblesPorElemento(
            eq(HILO_ID), eq("superestructura.losa"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(cRaiz)));

    PaginaComentariosDTO paginaSinFiltro =
        servicio.listarComentariosInspeccion(INSPECCION_ID, "   ", 0, 20);
    assertThat(paginaSinFiltro.contenido()).hasSize(1);

    PaginaComentariosDTO paginaConFiltro =
        servicio.listarComentariosInspeccion(INSPECCION_ID, " superestructura.losa ", 0, 20);
    assertThat(paginaConFiltro.contenido()).hasSize(1);
    assertThat(paginaConFiltro.contenido().getFirst().elementoRef())
        .isEqualTo("superestructura.losa");
  }

  @Test
  void crearComentarioInspeccion_validacionesAnclajeHerenciaYMenciones() {
    assertThatThrownBy(() -> servicio.crearComentarioInspeccion(INSPECCION_ID, AUTOR_ID, null))
        .isInstanceOf(NegocioException.class);
    assertThatThrownBy(
            () ->
                servicio.crearComentarioInspeccion(
                    INSPECCION_ID, AUTOR_ID, new CrearComentarioDTO(null)))
        .isInstanceOf(NegocioException.class);
    assertThatThrownBy(
            () ->
                servicio.crearComentarioInspeccion(
                    INSPECCION_ID, AUTOR_ID, new CrearComentarioDTO("   ")))
        .isInstanceOf(NegocioException.class);
    assertThatThrownBy(
            () ->
                servicio.crearComentarioInspeccion(
                    INSPECCION_ID,
                    AUTOR_ID,
                    new CrearComentarioDTO("Texto", null, "x".repeat(201))))
        .isInstanceOf(NegocioException.class);

    when(hiloRepositorio.existeUsuario(AUTOR_ID)).thenReturn(false);
    assertThatThrownBy(
            () ->
                servicio.crearComentarioInspeccion(
                    INSPECCION_ID, AUTOR_ID, new CrearComentarioDTO("Texto", null, "   ")))
        .isInstanceOf(NegocioException.class);

    when(hiloRepositorio.existeUsuario(AUTOR_ID)).thenReturn(true);
    when(hiloRepositorio.findByInspeccionId(INSPECCION_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                servicio.crearComentarioInspeccion(
                    INSPECCION_ID, AUTOR_ID, new CrearComentarioDTO("Texto")))
        .isInstanceOf(NegocioException.class);

    Hilo hiloCerrado = new Hilo();
    setId(hiloCerrado, HILO_ID);
    hiloCerrado.setCerradoEn(Instant.now());
    when(hiloRepositorio.findByInspeccionId(INSPECCION_ID)).thenReturn(Optional.of(hiloCerrado));
    assertThatThrownBy(
            () ->
                servicio.crearComentarioInspeccion(
                    INSPECCION_ID, AUTOR_ID, new CrearComentarioDTO("Texto")))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

    Hilo hiloAbierto = new Hilo();
    setId(hiloAbierto, HILO_ID);
    hiloAbierto.setPuenteId(PUENTE_ID);
    hiloAbierto.setInspeccionId(INSPECCION_ID);
    when(hiloRepositorio.findByInspeccionId(INSPECCION_ID)).thenReturn(Optional.of(hiloAbierto));

    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                servicio.crearComentarioInspeccion(
                    INSPECCION_ID, AUTOR_ID, new CrearComentarioDTO("Texto", COMENTARIO_ID)))
        .isInstanceOf(NegocioException.class);

    Comentario padreOtroHilo = new Comentario();
    Hilo otroHilo = new Hilo();
    setId(otroHilo, UUID.randomUUID());
    padreOtroHilo.setHilo(otroHilo);
    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(padreOtroHilo));
    assertThatThrownBy(
            () ->
                servicio.crearComentarioInspeccion(
                    INSPECCION_ID, AUTOR_ID, new CrearComentarioDTO("Texto", COMENTARIO_ID)))
        .isInstanceOf(NegocioException.class);

    Comentario padreOculto = new Comentario();
    padreOculto.setHilo(hiloAbierto);
    padreOculto.setOcultoEn(Instant.now());
    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(padreOculto));
    assertThatThrownBy(
            () ->
                servicio.crearComentarioInspeccion(
                    INSPECCION_ID, AUTOR_ID, new CrearComentarioDTO("Texto", COMENTARIO_ID)))
        .isInstanceOf(NegocioException.class);

    Comentario padreEliminado = new Comentario();
    padreEliminado.setHilo(hiloAbierto);
    padreEliminado.setEliminadoEn(Instant.now());
    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(padreEliminado));
    assertThatThrownBy(
            () ->
                servicio.crearComentarioInspeccion(
                    INSPECCION_ID, AUTOR_ID, new CrearComentarioDTO("Texto", COMENTARIO_ID)))
        .isInstanceOf(NegocioException.class);

    Comentario padreQueEsRespuesta = new Comentario();
    padreQueEsRespuesta.setHilo(hiloAbierto);
    padreQueEsRespuesta.setRespuestaA(new Comentario());
    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(padreQueEsRespuesta));
    assertThatThrownBy(
            () ->
                servicio.crearComentarioInspeccion(
                    INSPECCION_ID, AUTOR_ID, new CrearComentarioDTO("Texto", COMENTARIO_ID)))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

    Comentario padreValido = new Comentario();
    setId(padreValido, COMENTARIO_ID);
    padreValido.setHilo(hiloAbierto);
    padreValido.setElementoRef("subestructura.estribo_entrada");
    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(padreValido));
    when(comentarioRepositorio.saveAndFlush(any(Comentario.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(mapper.aDTO(any(Comentario.class)))
        .thenReturn(
            new ComentarioDTO(
                UUID.randomUUID(),
                HILO_ID,
                AUTOR_ID,
                "Respuesta",
                Instant.now(),
                COMENTARIO_ID,
                false,
                "Ana",
                "subestructura.estribo_entrada"));
    when(hiloRepositorio.filtrarUsuariosActivos(any())).thenReturn(List.of(MENCION_1));

    ComentarioDTO respuestaHeredada =
        servicio.crearComentarioInspeccion(
            INSPECCION_ID,
            AUTOR_ID,
            new CrearComentarioDTO("Hola @{" + MENCION_1 + "}", COMENTARIO_ID, null));
    assertThat(respuestaHeredada.elementoRef()).isEqualTo("subestructura.estribo_entrada");
    verify(notificacionService)
        .notificar(
            MENCION_1,
            ForoService.TIPO_NOTIFICACION_MENCION,
            ForoService.MENSAJE_MENCION_FORO,
            "/inspecciones/" + INSPECCION_ID + "/foro");

    ComentarioDTO respuestaConElementoExplicito =
        servicio.crearComentarioInspeccion(
            INSPECCION_ID,
            AUTOR_ID,
            new CrearComentarioDTO("Otra respuesta", COMENTARIO_ID, "cauce"));
    assertThat(respuestaConElementoExplicito).isNotNull();
  }

  @Test
  void etapa5_alertaPuenteMaloYMencionesEnCreacionYEdicion() {
    when(hiloRepositorio.existePuente(PUENTE_ID)).thenReturn(true);
    when(hiloRepositorio.existeUsuario(AUTOR_ID)).thenReturn(true);

    Hilo hiloAlerta = new Hilo();
    setId(hiloAlerta, HILO_ID);
    hiloAlerta.setPuenteId(PUENTE_ID);
    hiloAlerta.setAutorId(AUTOR_ID);
    hiloAlerta.setCategoria(CategoriaHilo.ALERTA);
    hiloAlerta.setTitulo("Socavación severa");
    when(hiloRepositorio.saveAndFlush(any(Hilo.class))).thenReturn(hiloAlerta);
    when(mapper.aDTO(hiloAlerta))
        .thenReturn(
            new HiloDTO(
                HILO_ID,
                PUENTE_ID,
                "Socavación severa",
                CategoriaHilo.ALERTA,
                AUTOR_ID,
                Instant.now(),
                false));

    // Caso 1: Puente en estado BUENO -> no notifica
    when(hiloRepositorio.obtenerEstadoActualPuente(PUENTE_ID)).thenReturn(Optional.of("BUENO"));
    servicio.crearHilo(
        PUENTE_ID, AUTOR_ID, new CrearHiloDTO("Socavación severa", CategoriaHilo.ALERTA));
    verify(notificacionService, never()).notificar(any(), any(), any(), any());

    // Caso 2: Puente en estado MALO pero sin destinatarios activos -> no notifica
    when(hiloRepositorio.obtenerEstadoActualPuente(PUENTE_ID)).thenReturn(Optional.of("MALO"));
    when(hiloRepositorio.buscarDestinatariosAlertaPuente()).thenReturn(List.of());
    servicio.crearHilo(
        PUENTE_ID, AUTOR_ID, new CrearHiloDTO("Socavación severa", CategoriaHilo.ALERTA));
    verify(notificacionService, never()).notificar(any(), any(), any(), any());

    // Caso 3: Puente en estado MALO con destinatarios -> notifica deduplicando
    when(hiloRepositorio.buscarDestinatariosAlertaPuente())
        .thenReturn(List.of(MENCION_1, MENCION_1, MENCION_2));
    when(hiloRepositorio.obtenerNombrePuente(PUENTE_ID)).thenReturn(Optional.of("Puente Samalá"));
    servicio.crearHilo(
        PUENTE_ID, AUTOR_ID, new CrearHiloDTO("Socavación severa", CategoriaHilo.ALERTA));
    verify(notificacionService)
        .notificar(
            MENCION_1,
            ForoService.TIPO_NOTIFICACION_ALERTA_PUENTE,
            "Alerta en puente Puente Samalá (estado MALO): Socavación severa",
            "/puentes/" + PUENTE_ID + "/foro/" + HILO_ID);
    verify(notificacionService)
        .notificar(
            MENCION_2,
            ForoService.TIPO_NOTIFICACION_ALERTA_PUENTE,
            "Alerta en puente Puente Samalá (estado MALO): Socavación severa",
            "/puentes/" + PUENTE_ID + "/foro/" + HILO_ID);

    // Caso 4: Menciones en crearComentario (ignora automención e inactivos)
    when(hiloRepositorio.findByIdAndInspeccionIdIsNull(HILO_ID))
        .thenReturn(Optional.of(hiloAlerta));
    Comentario comentarioGuardado = new Comentario();
    setId(comentarioGuardado, COMENTARIO_ID);
    comentarioGuardado.setHilo(hiloAlerta);
    comentarioGuardado.setAutorId(AUTOR_ID);
    comentarioGuardado.setTexto("Revisar @{" + AUTOR_ID + "} @{" + MENCION_1 + "} y @" + MENCION_2);
    when(comentarioRepositorio.saveAndFlush(any(Comentario.class))).thenReturn(comentarioGuardado);
    when(mapper.aDTO(comentarioGuardado))
        .thenReturn(
            new ComentarioDTO(
                COMENTARIO_ID, HILO_ID, AUTOR_ID, comentarioGuardado.getTexto(), Instant.now()));

    // Si ninguno está activo, no notifica
    when(hiloRepositorio.filtrarUsuariosActivos(any())).thenReturn(List.of());
    servicio.crearComentario(
        HILO_ID, AUTOR_ID, new CrearComentarioDTO(comentarioGuardado.getTexto()));
    verify(notificacionService, never())
        .notificar(any(), eq(ForoService.TIPO_NOTIFICACION_MENCION), any(), any());

    // Si MENCION_1 está activo y MENCION_2 inactivo, solo notifica a MENCION_1
    when(hiloRepositorio.filtrarUsuariosActivos(any())).thenReturn(List.of(MENCION_1));
    servicio.crearComentario(
        HILO_ID, AUTOR_ID, new CrearComentarioDTO(comentarioGuardado.getTexto()));
    verify(notificacionService)
        .notificar(
            MENCION_1,
            ForoService.TIPO_NOTIFICACION_MENCION,
            ForoService.MENSAJE_MENCION_FORO,
            "/puentes/" + PUENTE_ID + "/foro/" + HILO_ID);

    // Caso 5: Edición de comentario (Política B3: solo notifica menciones recién agregadas)
    setCreadoEn(comentarioGuardado, Instant.parse("2026-10-12T10:00:00Z"));
    comentarioGuardado.setTexto("Mención original @{" + MENCION_1 + "}");
    when(comentarioRepositorio.buscarParaModificar(COMENTARIO_ID))
        .thenReturn(Optional.of(comentarioGuardado));
    when(hiloRepositorio.filtrarUsuariosActivos(any())).thenReturn(List.of(MENCION_2));

    servicio.editarComentario(
        COMENTARIO_ID,
        AUTOR_ID,
        new EditarComentarioDTO(
            "Mención original @{" + MENCION_1 + "} y nueva @{" + MENCION_2 + "}"));

    verify(notificacionService)
        .notificar(
            MENCION_2,
            ForoService.TIPO_NOTIFICACION_MENCION,
            ForoService.MENSAJE_MENCION_FORO,
            "/puentes/" + PUENTE_ID + "/foro/" + HILO_ID);

    assertThat(ForoService.extraerMenciones(null)).isEmpty();
    assertThat(ForoService.extraerMenciones("   ")).isEmpty();
  }
}
