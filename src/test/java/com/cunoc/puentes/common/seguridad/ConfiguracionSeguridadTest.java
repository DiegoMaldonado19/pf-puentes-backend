package com.cunoc.puentes.common.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;

class ConfiguracionSeguridadTest {

  private static final String CONTRASENA = "Clave12345";

  private final PasswordEncoder codificador = new ConfiguracionSeguridad().passwordEncoder();

  @Test
  void laContrasenaSeCodificaConBcryptFactor12() {
    String hash = codificador.encode(CONTRASENA);

    assertThat(hash).startsWith("$2a$12$");
    assertThat(BCrypt.checkpw(CONTRASENA, hash)).isTrue();
    assertThat(codificador.matches(CONTRASENA, hash)).isTrue();
    assertThat(codificador.matches("otra-clave", hash)).isFalse();
  }

  @Test
  void dosCodificacionesDeLaMismaContrasenaSonDistintas() {
    assertThat(codificador.encode(CONTRASENA)).isNotEqualTo(codificador.encode(CONTRASENA));
  }

  @Test
  void laAplicacionNoArrancaConUnaClaveDeFirmaDebil() {
    for (String secreto : new String[] {"", "clave-corta-secreta"}) {
      new ApplicationContextRunner()
          .withUserConfiguration(TokenService.class)
          .withPropertyValues("sgp.jwt.secreto=" + secreto)
          .run(
              contexto -> {
                assertThat(contexto).hasFailed();
                String mensajes = mensajes(contexto.getStartupFailure());
                assertThat(mensajes).contains("JWT_SECRET").contains("32 bytes");
                if (!secreto.isEmpty()) {
                  assertThat(mensajes).doesNotContain(secreto);
                }
              });
    }
  }

  private static String mensajes(Throwable error) {
    StringBuilder todos = new StringBuilder();
    for (Throwable t = error; t != null; t = t.getCause()) {
      todos.append(t.getMessage()).append('\n');
    }
    return todos.toString();
  }
}
