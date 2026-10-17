package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.correo.EnviadorDeCorreo;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Reemplaza el envío real: guarda los correos para que la prueba lea el enlace. */
@TestConfiguration(proxyBeanMethods = false)
public class CorreosDePrueba {

  public record Correo(String destinatario, String asunto, String texto) {}

  /** Los correos salen en otro hilo (@Async), por eso la espera con tiempo límite. */
  public static class Buzon implements EnviadorDeCorreo {

    private final BlockingQueue<Correo> enviados = new LinkedBlockingQueue<>();

    @Override
    public void enviar(String destinatario, String asunto, String texto) {
      enviados.add(new Correo(destinatario, asunto, texto));
    }

    /** El siguiente correo, o {@code null} si no llega en {@code milisegundos}. */
    public Correo siguiente(long milisegundos) throws InterruptedException {
      return enviados.poll(milisegundos, TimeUnit.MILLISECONDS);
    }

    public void vaciar() {
      enviados.clear();
    }
  }

  @Bean
  @Primary
  Buzon buzon() {
    return new Buzon();
  }
}
