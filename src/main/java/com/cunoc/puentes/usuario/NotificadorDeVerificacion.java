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
 * Envía el enlace de verificación una vez confirmada la transacción y fuera del hilo de la
 * petición: así la respuesta no tarda más, ni delata si el correo existe, cuando el SMTP es lento.
 * Si el envío falla la cuenta ya existe y el estudiante puede pedir el reenvío.
 */
@Component
class NotificadorDeVerificacion {

  private static final Logger log = LoggerFactory.getLogger(NotificadorDeVerificacion.class);
  private static final String RUTA_FRONTEND = "/verificar-correo";

  private final EnviadorDeCorreo enviador;
  private final String urlFrontend;

  NotificadorDeVerificacion(
      EnviadorDeCorreo enviador, @Value("${sgp.frontend.url}") String urlFrontend) {
    this.enviador = enviador;
    this.urlFrontend = urlFrontend;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  void enviar(VerificacionEmitida evento) {
    try {
      enviador.enviar(evento.correo(), "Confirma tu correo en el SGP", texto(evento));
    } catch (RuntimeException e) {
      // Sin el mensaje de la excepción: el SMTP suele repetir el destinatario (DT-SEC-12)
      log.error(
          "No se pudo enviar la verificación de la cuenta {} ({})",
          evento.usuarioId(),
          e.getClass().getSimpleName());
    }
  }

  private String texto(VerificacionEmitida evento) {
    String enlace =
        UriComponentsBuilder.fromUriString(urlFrontend)
            .path(RUTA_FRONTEND)
            .queryParam("token", evento.token())
            .build()
            .toUriString();
    return """
        Hola %s,

        Para confirmar tu correo abre este enlace. Es de un solo uso y vence en %d horas:
        %s

        Si no creaste una cuenta en el Sistema de Gestión de Puentes, ignora este mensaje.
        """
        .formatted(evento.nombre(), evento.vigencia().toHours(), enlace);
  }
}
