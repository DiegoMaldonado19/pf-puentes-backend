package com.cunoc.puentes.common.correo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Envía por SMTP con la configuración de {@code spring.mail.*} (DT-BE-15). */
@Component
@ConditionalOnProperty(name = "sgp.correo.modo", havingValue = "smtp")
class EnviadorSmtp implements EnviadorDeCorreo {

  private final JavaMailSender remitente;
  private final String desde;

  EnviadorSmtp(JavaMailSender remitente, @Value("${sgp.correo.remitente}") String desde) {
    this.remitente = remitente;
    this.desde = desde;
  }

  @Override
  public void enviar(String destinatario, String asunto, String texto) {
    SimpleMailMessage mensaje = new SimpleMailMessage();
    mensaje.setFrom(desde);
    mensaje.setTo(destinatario);
    mensaje.setSubject(asunto);
    mensaje.setText(texto);
    remitente.send(mensaje);
  }
}
