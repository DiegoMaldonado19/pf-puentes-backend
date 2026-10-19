package com.cunoc.puentes.common.correo;

/**
 * Envío de correos de texto plano. Dos implementaciones: SMTP y, para desarrollo, la consola
 * ({@code sgp.correo.modo}). Lo usan los flujos de verificación, invitación y recuperación.
 */
public interface EnviadorDeCorreo {

  /** Lanza una excepción si el correo no pudo entregarse al servidor de salida. */
  void enviar(String destinatario, String asunto, String texto);
}
