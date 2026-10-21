package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.correo.EnviadorDeCorreo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Envía el enlace de invitación una vez confirmada la transacción y fuera del hilo de la petición.
 * Si el envío falla, la invitación ya existe y el Administrador puede revocarla y volver a invitar.
 */
@Component
class NotificadorDeInvitacion {

  private static final Logger log = LoggerFactory.getLogger(NotificadorDeInvitacion.class);
  private static final String RUTA_FRONTEND = "/aceptar-invitacion";

  private final EnviadorDeCorreo enviador;
  private final String urlFrontend;

  NotificadorDeInvitacion(
      EnviadorDeCorreo enviador, @Value("${sgp.frontend.url}") String urlFrontend) {
    this.enviador = enviador;
    this.urlFrontend = urlFrontend;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  void enviar(InvitacionEmitida evento) {
    try {
      enviador.enviar(
          evento.correo(), "Invitación al Sistema de Gestión de Puentes", texto(evento));
    } catch (RuntimeException e) {
      // Sin el mensaje de la excepción: el SMTP suele repetir el destinatario (DT-SEC-12)
      log.error(
          "No se pudo enviar la invitación {} ({})",
          evento.invitacionId(),
          e.getClass().getSimpleName());
    }
  }

  private String texto(InvitacionEmitida evento) {
    String enlace =
        UriComponentsBuilder.fromUriString(urlFrontend)
            .path(RUTA_FRONTEND)
            .queryParam("token", evento.token())
            .build()
            .toUriString();
    return """
        Hola,

        Te invitaron al Sistema de Gestión de Puentes como %s. Para aceptar abre este enlace. Es de un solo uso y vence en %d días:
        %s

        Si no esperabas esta invitación, ignora este mensaje.
        """
        .formatted(nombreDelRol(evento.rol()), Math.max(evento.vigencia().toDays(), 1), enlace);
  }

  private static String nombreDelRol(Rol rol) {
    return switch (rol) {
      case PROFESIONAL_EXTERNO -> "Profesional externo";
      case CATEDRATICO -> "Catedrático";
      case ADMINISTRADOR -> "Administrador";
      case ESTUDIANTE -> "Estudiante";
    };
  }
}
