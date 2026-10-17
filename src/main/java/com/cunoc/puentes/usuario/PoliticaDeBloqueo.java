package com.cunoc.puentes.usuario;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * RN-USR-07: cuántos intentos fallidos, en qué ventana y cuánto dura el bloqueo. Solo calcula
 * instantes y plazos; el conteo lo hace la base en una sola sentencia ({@link
 * UsuarioRepository#registrarFallo}).
 */
@Component
class PoliticaDeBloqueo {

  private final int maximo;
  private final Duration ventana;
  private final Duration bloqueo;

  PoliticaDeBloqueo(
      @Value("${sgp.login.intentos-maximos:5}") int maximo,
      @Value("${sgp.login.ventana:PT15M}") Duration ventana,
      @Value("${sgp.login.bloqueo:PT15M}") Duration bloqueo) {
    this.maximo = maximo;
    this.ventana = ventana;
    this.bloqueo = bloqueo;
  }

  int maximo() {
    return maximo;
  }

  /** Un primer fallo de este instante o anterior ya quedó fuera de la ventana. */
  Instant inicioDeVentana(Instant ahora) {
    return ahora.minus(ventana);
  }

  Instant bloqueadoHasta(Instant ahora) {
    return ahora.plus(bloqueo);
  }

  static boolean estaBloqueada(Instant bloqueadoHasta, Instant ahora) {
    return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
  }

  /**
   * Minutos que faltan, redondeados hacia arriba: nunca dice "0 minutos" a quien sigue bloqueado.
   */
  static long minutosRestantes(Instant bloqueadoHasta, Instant ahora) {
    long segundos = Duration.between(ahora, bloqueadoHasta).toSeconds();
    return Math.max(1, (segundos + 59) / 60);
  }
}
