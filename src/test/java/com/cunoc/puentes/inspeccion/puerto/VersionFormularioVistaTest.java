package com.cunoc.puentes.inspeccion.puerto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VersionFormularioVistaTest {

  private static final UUID ID = UUID.fromString("0199b000-0000-7000-8000-00000000f001");

  @Test
  void copiaElEsquemaYAceptaNuloComoVacio() {
    Map<String, Object> origen = new HashMap<>();
    origen.put("type", "object");
    VersionFormularioVista vista = new VersionFormularioVista(ID, "SIECA-PRUEBA", origen);
    origen.put("extra", true);
    assertThat(vista.esquema()).containsEntry("type", "object").doesNotContainKey("extra");
    assertThat(new VersionFormularioVista(ID, "SIECA-PRUEBA", null).esquema()).isEmpty();
  }

  @Test
  void elGrupoCopiaLasRutas() {
    GrupoElemento grupo = new GrupoElemento("estribo", Set.of("a.b"));
    assertThat(grupo.rutasDano()).containsExactly("a.b");
    assertThat(new GrupoElemento("vacio", null).rutasDano()).isEmpty();
  }

  @Test
  void exigeIdentidad() {
    assertThatThrownBy(() -> new VersionFormularioVista(null, "x", Map.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new GrupoElemento(null, Set.of()))
        .isInstanceOf(NullPointerException.class);
  }
}
