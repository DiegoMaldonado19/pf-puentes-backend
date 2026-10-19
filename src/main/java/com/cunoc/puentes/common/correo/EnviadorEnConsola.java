package com.cunoc.puentes.common.correo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Solo para desarrollo: imprime el correo, enlace incluido, en el log. Es la única excepción a
 * DT-SEC-12, por eso la aplicación no arranca con el perfil {@code prod}.
 */
@Component
@ConditionalOnProperty(name = "sgp.correo.modo", havingValue = "consola", matchIfMissing = true)
class EnviadorEnConsola implements EnviadorDeCorreo {

  private static final Logger log = LoggerFactory.getLogger(EnviadorEnConsola.class);

  EnviadorEnConsola(Environment entorno) {
    if (entorno.acceptsProfiles(Profiles.of("prod"))) {
      throw new IllegalStateException(
          "sgp.correo.modo=consola imprime los enlaces en el log: en prod usa CORREO_MODO=smtp");
    }
    log.warn("Los correos no se envían: se imprimen en el log (sgp.correo.modo=consola)");
  }

  @Override
  public void enviar(String destinatario, String asunto, String texto) {
    log.info("Correo para {}\nAsunto: {}\n{}", destinatario, asunto, texto);
  }
}
