package com.cunoc.puentes;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Lanza un recorrido de Playwright contra el frontend real y espera su resultado. */
final class NavegadorPlaywright {

  private NavegadorPlaywright() {}

  static Path frontend() {
    return Path.of(System.getenv().getOrDefault("P8_FRONTEND_DIR", "../pf-puentes-frontend"))
        .toAbsolutePath()
        .normalize();
  }

  static void ejecutar(Path frontend, String configuracion, Path log, Map<String, String> variables)
      throws Exception {
    Files.createDirectories(log.getParent());
    var constructor =
        new ProcessBuilder("npm", "exec", "--", "playwright", "test", "-c", configuracion)
            .directory(frontend.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile());
    constructor.environment().putAll(variables);
    Process proceso = constructor.start();
    try {
      assertThat(proceso.waitFor(240, TimeUnit.SECONDS))
          .as("Playwright debe terminar antes de cuatro minutos")
          .isTrue();
      assertThat(proceso.exitValue()).as("Recorrido real del navegador").isZero();
    } finally {
      proceso.descendants().forEach(ProcessHandle::destroy);
      proceso.destroy();
    }
  }
}
