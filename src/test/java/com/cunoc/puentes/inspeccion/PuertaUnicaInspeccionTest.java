package com.cunoc.puentes.inspeccion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PuertaUnicaInspeccionTest {

  @Test
  void ningunControladorNiServicioAsignaElEstadoDirectamente() throws IOException {
    Path raiz = Path.of("src/main/java/com/cunoc/puentes/inspeccion");
    List<String> asignaciones = new ArrayList<>();
    try (Stream<Path> archivos = Files.walk(raiz)) {
      archivos
          .filter(ruta -> ruta.toString().endsWith(".java"))
          .filter(ruta -> !ruta.getFileName().toString().equals("Inspeccion.java"))
          .forEach(
              ruta -> {
                try {
                  List<String> lineas = Files.readAllLines(ruta);
                  for (int i = 0; i < lineas.size(); i++) {
                    if (lineas.get(i).contains(".setEstado(")) {
                      asignaciones.add(ruta + ":" + (i + 1));
                    }
                  }
                } catch (IOException e) {
                  throw new IllegalStateException(e);
                }
              });
    }
    assertThat(asignaciones).isEmpty();
  }
}
