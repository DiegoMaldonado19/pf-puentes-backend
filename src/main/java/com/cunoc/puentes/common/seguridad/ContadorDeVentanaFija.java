package com.cunoc.puentes.common.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Ventana fija por clave (IP + ruta). No se comparte entre réplicas: ver ADR 0026. */
final class ContadorDeVentanaFija {

  private static final int PURGA_CADA = 64;

  record Decision(boolean admitida, long reintentoSegundos) {}

  private record Ventana(Instant inicio, int cuenta, Duration duracion) {
    boolean vencida(Instant ahora) {
      return !ahora.isBefore(inicio.plus(duracion));
    }
  }

  private final ConcurrentHashMap<String, Ventana> ventanas = new ConcurrentHashMap<>();
  private final AtomicLong accesos = new AtomicLong();

  Decision registrar(String clave, int maximo, Duration duracion, Instant ahora) {
    Ventana ventana =
        ventanas.compute(
            clave,
            (k, actual) -> {
              if (actual == null || actual.vencida(ahora)) {
                return new Ventana(ahora, 1, duracion);
              }
              return new Ventana(actual.inicio(), actual.cuenta() + 1, actual.duracion());
            });
    if (accesos.incrementAndGet() % PURGA_CADA == 0) {
      purgar(ahora);
    }
    long reintento = Duration.between(ahora, ventana.inicio().plus(ventana.duracion())).toSeconds();
    return new Decision(ventana.cuenta() <= maximo, Math.max(reintento, 1));
  }

  void purgar(Instant ahora) {
    ventanas.entrySet().removeIf(entrada -> entrada.getValue().vencida(ahora));
  }

  int tamaño() {
    return ventanas.size();
  }
}
