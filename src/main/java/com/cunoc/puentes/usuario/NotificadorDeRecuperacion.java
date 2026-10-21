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
 * Envía el enlace de recuperación una vez confirmada la transacción y fuera del hilo de la
 * petición: la respuesta 202 no delata si el correo existe, aunque el SMTP sea lento.
 */
@Component
class NotificadorDeRecuperacion {

  private static final Logger log = LoggerFactory.getLogger(NotificadorDeRecuperacion.class);
  private static final String RUTA_FRONTEND = "/restablecer-contrasena";

  private final EnviadorDeCorreo enviador;
  private final String urlFrontend;

  NotificadorDeRecuperacion(
      EnviadorDeCorreo enviador, @Value("${sgp.frontend.url}") String urlFrontend) {
    this.enviador = enviador;
    this.urlFrontend = urlFrontend;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  void enviar(RecuperacionEmitida evento) {
    try {
      enviador.enviar(evento.correo(), "Restablece tu contraseña en el SGP", texto(evento));
    } catch (RuntimeException e) {
      // Sin el mensaje de la excepción: el SMTP suele repetir el destinatario (DT-SEC-12)
      log.error(
          "No se pudo enviar la recuperación de la cuenta {} ({})",
          evento.usuarioId(),
          e.getClass().getSimpleName());
    }
  }

  private String texto(RecuperacionEmitida evento) {
    String enlace =
        UriComponentsBuilder.fromUriString(urlFrontend)
            .path(RUTA_FRONTEND)
            .queryParam("token", evento.token())
            .build()
            .toUriString();
    long horas = Math.max(evento.vigencia().toHours(), 1);
    return """
        Hola %s,

        Para restablecer tu contraseña abre este enlace. Es de un solo uso y vence en %d %s:
        %s

        Si no pediste este cambio, ignora este mensaje. Tu contraseña actual sigue vigente.
        """
        .formatted(evento.nombre(), horas, horas == 1 ? "hora" : "horas", enlace);
  }
}
