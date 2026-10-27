package com.cunoc.puentes.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.common.NegocioException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CamposRevisionTest {
  @Test
  void conservaReferenciaCanonicaYValorOriginal() {
    var campos =
        CamposRevision.extraer(
            Map.of(
                "properties",
                Map.of(
                    "material",
                    Map.of(
                        "type",
                        "string",
                        "x-elemento-ref",
                        "estribo.material",
                        "title",
                        "Material"))),
            Map.of("material", "Acero"));
    assertThat(campos)
        .containsExactly(
            new DetalleRevisionDTO.CampoRevision("estribo.material", "Material", "Acero"));
  }

  @Test
  void usaRutaRealParaCamposSinReferenciaYAnclaArregloCompleto() {
    var campos =
        CamposRevision.extraer(
            Map.of(
                "properties",
                Map.of("fotos", Map.of("type", "array", "items", Map.of("type", "string")))),
            Map.of("fotos", List.of("a", "b")));
    assertThat(campos)
        .containsExactly(new DetalleRevisionDTO.CampoRevision("fotos", "fotos", List.of("a", "b")));
  }

  @Test
  void rechazaReferenciasDuplicadasYNoInventaCamposDesdeDefiniciones() {
    var campo = Map.of("type", "string", "x-elemento-ref", "duplicado");
    assertThatThrownBy(
            () ->
                CamposRevision.extraer(
                    Map.of("properties", Map.of("a", campo, "b", campo)), Map.of()))
        .isInstanceOf(NegocioException.class);
    assertThat(CamposRevision.extraer(Map.of("$defs", Map.of("a", campo)), Map.of())).isEmpty();
  }
}
