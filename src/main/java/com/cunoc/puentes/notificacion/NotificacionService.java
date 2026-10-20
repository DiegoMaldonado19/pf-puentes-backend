package com.cunoc.puentes.notificacion;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.notificacion.dto.ConteoNoLeidasDTO;
import com.cunoc.puentes.notificacion.dto.NotificacionDTO;
import com.cunoc.puentes.notificacion.dto.PaginaNotificacionesDTO;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificacionService {

  static final int MAX_LONGITUD_TIPO = 50;
  static final int MAX_LONGITUD_ENLACE = 300;
  static final int MAX_TAMANO_PAGINA = 100;
  static final long MAX_OFFSET_SOPORTADO = Integer.MAX_VALUE;

  private final NotificacionRepository repositorio;
  private final NotificacionMapper mapper;

  NotificacionService(NotificacionRepository repositorio, NotificacionMapper mapper) {
    this.repositorio = repositorio;
    this.mapper = mapper;
  }

  public void notificar(UUID usuarioId, String tipo, String mensaje, String enlace) {
    validarNotificacion(usuarioId, tipo, mensaje, enlace);

    if (!repositorio.existeUsuario(usuarioId)) {
      throw new NegocioException(
          HttpStatus.NOT_FOUND,
          "usuario-no-encontrado",
          "Destinatario no encontrado",
          "El usuario destinatario no existe en el sistema.");
    }

    Notificacion notificacion = new Notificacion();
    notificacion.setUsuarioId(usuarioId);
    notificacion.setTipo(tipo.trim());
    notificacion.setMensaje(mensaje.trim());
    notificacion.setEnlace(enlace != null ? enlace.trim() : null);

    repositorio.save(notificacion);
  }

  @Transactional(readOnly = true)
  public PaginaNotificacionesDTO listar(UUID usuarioId, int page, int size) {
    if (page < 0 || size <= 0 || size > MAX_TAMANO_PAGINA) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "paginacion-invalida",
          "Paginación inválida",
          "Los parámetros de paginación deben cumplir page >= 0 y 1 <= size <= 100.");
    }

    if ((long) page * (long) size > MAX_OFFSET_SOPORTADO) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "paginacion-invalida",
          "Paginación inválida",
          "El desplazamiento solicitado supera el límite máximo soportado por el sistema.");
    }

    Pageable pageable =
        PageRequest.of(page, size, Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("id")));

    Page<Notificacion> resultado = repositorio.findByUsuarioId(usuarioId, pageable);
    List<NotificacionDTO> contenido = resultado.getContent().stream().map(mapper::aDTO).toList();

    return new PaginaNotificacionesDTO(
        contenido,
        resultado.getNumber(),
        resultado.getSize(),
        resultado.getTotalElements(),
        resultado.getTotalPages());
  }

  @Transactional(readOnly = true)
  public ConteoNoLeidasDTO contarNoLeidas(UUID usuarioId) {
    long total = repositorio.countByUsuarioIdAndLeidaEnIsNull(usuarioId);
    return new ConteoNoLeidasDTO(total);
  }

  public NotificacionDTO marcarComoLeida(UUID id, UUID usuarioId) {
    Notificacion notificacion =
        repositorio
            .findByIdAndUsuarioIdParaLectura(id, usuarioId)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "notificacion-no-encontrada",
                        "Notificación no encontrada",
                        "No existe la notificación especificada para el usuario actual."));

    if (notificacion.getLeidaEn() == null) {
      notificacion.setLeidaEn(Instant.now().truncatedTo(ChronoUnit.MICROS));
      notificacion = repositorio.saveAndFlush(notificacion);
    }

    return mapper.aDTO(notificacion);
  }

  private static void validarNotificacion(
      UUID usuarioId, String tipo, String mensaje, String enlace) {
    if (usuarioId == null) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "destinatario-requerido",
          "Destinatario requerido",
          "El identificador del usuario destinatario es obligatorio.");
    }
    if (tipo == null || tipo.isBlank()) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "tipo-requerido",
          "Tipo requerido",
          "El tipo de notificación no puede estar vacío.");
    }
    if (tipo.trim().length() > MAX_LONGITUD_TIPO) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "tipo-muy-largo",
          "Tipo demasiado largo",
          "El tipo de notificación no puede superar los 50 caracteres.");
    }
    if (mensaje == null || mensaje.isBlank()) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "mensaje-requerido",
          "Mensaje requerido",
          "El mensaje de notificación no puede estar vacío.");
    }
    if (enlace != null) {
      String enlaceLimpio = enlace.trim();
      if (enlaceLimpio.length() > MAX_LONGITUD_ENLACE) {
        throw new NegocioException(
            HttpStatus.BAD_REQUEST,
            "enlace-muy-largo",
            "Enlace demasiado largo",
            "El enlace no puede superar los 300 caracteres.");
      }
      if (!enlaceLimpio.startsWith("/") || enlaceLimpio.startsWith("//")) {
        throw new NegocioException(
            HttpStatus.BAD_REQUEST,
            "enlace-invalido",
            "Enlace inválido",
            "El enlace debe ser una ruta interna de la SPA que empiece con '/' y no con '//'.");
      }
    }
  }
}
