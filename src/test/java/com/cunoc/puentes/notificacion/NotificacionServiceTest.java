package com.cunoc.puentes.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.notificacion.dto.ConteoNoLeidasDTO;
import com.cunoc.puentes.notificacion.dto.NotificacionDTO;
import com.cunoc.puentes.notificacion.dto.PaginaNotificacionesDTO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

  private static final UUID USUARIO_ID = UUID.fromString("0192f5a0-0000-7000-8000-000000000001");
  private static final UUID OTRO_USUARIO_ID =
      UUID.fromString("0192f5a0-0000-7000-8000-000000000002");
  private static final UUID NOTIFICACION_ID =
      UUID.fromString("0192f5a0-0000-7000-8000-000000000010");

  @Mock private NotificacionRepository repositorio;
  @Mock private NotificacionMapper mapper;

  @InjectMocks private NotificacionService servicio;

  @Nested
  class Notificar {

    @Test
    void guardaNotificacionCorrectamenteConEnlace() {
      when(repositorio.existeUsuario(USUARIO_ID)).thenReturn(true);

      servicio.notificar(USUARIO_ID, "REVISION_PENDIENTE", "Tienes una revisión", "/revisiones/1");

      ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
      verify(repositorio).save(captor.capture());
      Notificacion guardada = captor.getValue();
      assertThat(guardada.getUsuarioId()).isEqualTo(USUARIO_ID);
      assertThat(guardada.getTipo()).isEqualTo("REVISION_PENDIENTE");
      assertThat(guardada.getMensaje()).isEqualTo("Tienes una revisión");
      assertThat(guardada.getEnlace()).isEqualTo("/revisiones/1");
      assertThat(guardada.getLeidaEn()).isNull();
    }

    @Test
    void guardaNotificacionSinEnlaceOpcional() {
      when(repositorio.existeUsuario(USUARIO_ID)).thenReturn(true);

      servicio.notificar(USUARIO_ID, "ALERTA", "Alerta general", null);

      ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
      verify(repositorio).save(captor.capture());
      assertThat(captor.getValue().getEnlace()).isNull();
    }

    @Test
    void fallaSiDestinatarioEsNulo() {
      assertThatThrownBy(() -> servicio.notificar(null, "TIPO", "Mensaje", null))
          .isInstanceOf(NegocioException.class)
          .satisfies(
              ex ->
                  assertThat(((NegocioException) ex).getStatusCode())
                      .isEqualTo(HttpStatus.BAD_REQUEST));
      verify(repositorio, never()).save(any());
    }

    @Test
    void fallaSiDestinatarioNoExisteEnModeloBase() {
      when(repositorio.existeUsuario(USUARIO_ID)).thenReturn(false);

      assertThatThrownBy(() -> servicio.notificar(USUARIO_ID, "TIPO", "Mensaje", null))
          .isInstanceOf(NegocioException.class)
          .satisfies(
              ex ->
                  assertThat(((NegocioException) ex).getStatusCode())
                      .isEqualTo(HttpStatus.NOT_FOUND));
      verify(repositorio, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void fallaSiTipoEsBlancoONulo(String tipoInvalido) {
      assertThatThrownBy(() -> servicio.notificar(USUARIO_ID, tipoInvalido, "Mensaje", null))
          .isInstanceOf(NegocioException.class)
          .satisfies(
              ex ->
                  assertThat(((NegocioException) ex).getStatusCode())
                      .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void fallaSiTipoSupera50Caracteres() {
      String tipoLargo = "A".repeat(51);
      assertThatThrownBy(() -> servicio.notificar(USUARIO_ID, tipoLargo, "Mensaje", null))
          .isInstanceOf(NegocioException.class)
          .satisfies(
              ex ->
                  assertThat(((NegocioException) ex).getStatusCode())
                      .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void fallaSiMensajeEsBlancoONulo(String mensajeInvalido) {
      assertThatThrownBy(() -> servicio.notificar(USUARIO_ID, "TIPO", mensajeInvalido, null))
          .isInstanceOf(NegocioException.class)
          .satisfies(
              ex ->
                  assertThat(((NegocioException) ex).getStatusCode())
                      .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void fallaSiEnlaceSupera300Caracteres() {
      String enlaceLargo = "/" + "a".repeat(301);
      assertThatThrownBy(() -> servicio.notificar(USUARIO_ID, "TIPO", "Mensaje", enlaceLargo))
          .isInstanceOf(NegocioException.class)
          .satisfies(
              ex ->
                  assertThat(((NegocioException) ex).getStatusCode())
                      .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest
    @ValueSource(strings = {"//malicioso.com", "https://externo.com", "puentes/1", "  "})
    void fallaSiEnlaceNoEsRutaInternaDeSpa(String enlaceInvalido) {
      assertThatThrownBy(() -> servicio.notificar(USUARIO_ID, "TIPO", "Mensaje", enlaceInvalido))
          .isInstanceOf(NegocioException.class)
          .satisfies(
              ex ->
                  assertThat(((NegocioException) ex).getStatusCode())
                      .isEqualTo(HttpStatus.BAD_REQUEST));
    }
  }

  @Nested
  class MarcarComoLeida {

    @Test
    void marcaComoLeidaPorPrimeraVezConBloqueoPesimista() {
      Notificacion notificacion = new Notificacion();
      notificacion.setUsuarioId(USUARIO_ID);
      notificacion.setLeidaEn(null);

      NotificacionDTO dtoEsperado =
          new NotificacionDTO(NOTIFICACION_ID, "TIPO", "Msg", null, Instant.now(), Instant.now());

      when(repositorio.findByIdAndUsuarioIdParaLectura(NOTIFICACION_ID, USUARIO_ID))
          .thenReturn(Optional.of(notificacion));
      when(repositorio.saveAndFlush(any(Notificacion.class))).thenAnswer(i -> i.getArgument(0));
      when(mapper.aDTO(any(Notificacion.class))).thenReturn(dtoEsperado);

      NotificacionDTO resultado = servicio.marcarComoLeida(NOTIFICACION_ID, USUARIO_ID);

      assertThat(resultado).isEqualTo(dtoEsperado);
      assertThat(notificacion.getLeidaEn()).isNotNull();
      verify(repositorio).saveAndFlush(notificacion);
    }

    @Test
    void conservaPrimeraFechaEnLlamadasRepetidas() {
      Instant primeraLectura = Instant.parse("2026-10-10T12:00:00Z");
      Notificacion notificacion = new Notificacion();
      notificacion.setUsuarioId(USUARIO_ID);
      notificacion.setLeidaEn(primeraLectura);

      NotificacionDTO dtoEsperado =
          new NotificacionDTO(NOTIFICACION_ID, "TIPO", "Msg", null, Instant.now(), primeraLectura);

      when(repositorio.findByIdAndUsuarioIdParaLectura(NOTIFICACION_ID, USUARIO_ID))
          .thenReturn(Optional.of(notificacion));
      when(mapper.aDTO(notificacion)).thenReturn(dtoEsperado);

      NotificacionDTO resultado = servicio.marcarComoLeida(NOTIFICACION_ID, USUARIO_ID);

      assertThat(resultado).isEqualTo(dtoEsperado);
      assertThat(notificacion.getLeidaEn()).isEqualTo(primeraLectura);
      verify(repositorio, never()).saveAndFlush(any());
      verify(repositorio, never()).save(any());
    }

    @Test
    void fallaCon404SiNoExiste() {
      when(repositorio.findByIdAndUsuarioIdParaLectura(NOTIFICACION_ID, USUARIO_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> servicio.marcarComoLeida(NOTIFICACION_ID, USUARIO_ID))
          .isInstanceOf(NegocioException.class)
          .satisfies(
              ex ->
                  assertThat(((NegocioException) ex).getStatusCode())
                      .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void fallaConElMismo404SiPerteneceAOtroUsuario() {
      when(repositorio.findByIdAndUsuarioIdParaLectura(NOTIFICACION_ID, OTRO_USUARIO_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> servicio.marcarComoLeida(NOTIFICACION_ID, OTRO_USUARIO_ID))
          .isInstanceOf(NegocioException.class)
          .satisfies(
              ex ->
                  assertThat(((NegocioException) ex).getStatusCode())
                      .isEqualTo(HttpStatus.NOT_FOUND));
    }
  }

  @Nested
  class ContarNoLeidas {

    @Test
    void devuelveConteoCorrecto() {
      when(repositorio.countByUsuarioIdAndLeidaEnIsNull(USUARIO_ID)).thenReturn(5L);

      ConteoNoLeidasDTO resultado = servicio.contarNoLeidas(USUARIO_ID);

      assertThat(resultado.total()).isEqualTo(5L);
    }
  }

  @Nested
  class Listar {

    @Test
    void listaPaginadaConOrdenDescendenteEstable() {
      Notificacion notificacion = new Notificacion();
      notificacion.setUsuarioId(USUARIO_ID);
      notificacion.setTipo("TIPO");
      notificacion.setMensaje("Mensaje");

      NotificacionDTO dto =
          new NotificacionDTO(NOTIFICACION_ID, "TIPO", "Mensaje", null, Instant.now(), null);

      Pageable pageable =
          PageRequest.of(0, 20, Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("id")));
      when(repositorio.findByUsuarioId(USUARIO_ID, pageable))
          .thenReturn(new PageImpl<>(List.of(notificacion), pageable, 1));
      when(mapper.aDTO(notificacion)).thenReturn(dto);

      PaginaNotificacionesDTO resultado = servicio.listar(USUARIO_ID, 0, 20);

      assertThat(resultado.contenido()).containsExactly(dto);
      assertThat(resultado.pagina()).isZero();
      assertThat(resultado.tamano()).isEqualTo(20);
      assertThat(resultado.totalElementos()).isEqualTo(1L);
      assertThat(resultado.totalPaginas()).isEqualTo(1);
    }

    @Test
    void rechazaPaginaNegativa() {
      assertThatThrownBy(() -> servicio.listar(USUARIO_ID, -1, 20))
          .isInstanceOf(NegocioException.class)
          .satisfies(
              ex ->
                  assertThat(((NegocioException) ex).getStatusCode())
                      .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10, 101, 200})
    void rechazaTamanoInvalido(int tamanoInvalido) {
      assertThatThrownBy(() -> servicio.listar(USUARIO_ID, 0, tamanoInvalido))
          .isInstanceOf(NegocioException.class)
          .satisfies(
              ex ->
                  assertThat(((NegocioException) ex).getStatusCode())
                      .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rechazaDesbordamientoDeOffsetConPageMaximo() {
      assertThatThrownBy(() -> servicio.listar(USUARIO_ID, Integer.MAX_VALUE, 100))
          .isInstanceOf(NegocioException.class)
          .satisfies(
              ex -> {
                NegocioException nEx = (NegocioException) ex;
                assertThat(nEx.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(nEx.getBody().getType().toString()).contains("paginacion-invalida");
              });
    }
  }
}
