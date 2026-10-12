package com.cunoc.puentes.common.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** DT-SEC-12: nada sensible llega a los valores anterior ni posterior. */
class SaneadorDeAuditoriaTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "contrasena_hash",
        "token_hash",
        "token_verificacion",
        "refresh_token",
        "TokenAcceso",
        "password",
        "jwt_secret",
        "clave_secreto"
      })
  void omiteLosCamposSensibles(String campo) {
    Map<String, Object> limpio =
        SaneadorDeAuditoria.sanear(Map.of(campo, "valor", "nombre", "Ana"));

    assertThat(limpio).containsOnlyKeys("nombre");
  }

  @Test
  void omiteLosCamposSensiblesAnidados() {
    Map<String, Object> limpio =
        SaneadorDeAuditoria.sanear(
            Map.of(
                "datos",
                Map.of("token", "x", "color", "rojo"),
                "lista",
                List.of(Map.of("hash", "y", "n", 1))));

    assertThat(limpio.get("datos")).isEqualTo(Map.of("color", "rojo"));
    assertThat(limpio.get("lista")).isEqualTo(List.of(Map.of("n", 1)));
  }

  @Test
  void enmascaraElCorreo() {
    Map<String, Object> limpio =
        SaneadorDeAuditoria.sanear(Map.of("correo", "ana.perez@correo.com"));

    assertThat(limpio).containsEntry("correo", "a***@correo.com");
  }

  @Test
  void enmascaraUnCorreoAunqueElCampoSeLlameDistinto() {
    Map<String, Object> limpio = SaneadorDeAuditoria.sanear(Map.of("contacto", "ana@correo.com"));

    assertThat(limpio).containsEntry("contacto", "a***@correo.com");
  }

  @Test
  void enmascaraPorCompletoUnCorreoSinArroba() {
    assertThat(SaneadorDeAuditoria.sanear(Map.of("email", "no-es-correo")))
        .containsEntry("email", SaneadorDeAuditoria.ENMASCARADO);
  }

  @Test
  void noTocaLaMarcaDeVerificacionDelCorreo() {
    Instant ahora = Instant.parse("2026-10-11T15:00:00Z");

    assertThat(SaneadorDeAuditoria.sanear(Map.of("correo_verificado_en", ahora)))
        .containsEntry("correo_verificado_en", "2026-10-11T15:00:00Z");
  }

  @Test
  void conservaLosValoresDeNegocioConvertidosATextoONumero() {
    UUID id = UUID.fromString("0192f5a0-0000-7000-8000-000000000002");
    Map<String, Object> origen = new LinkedHashMap<>();
    origen.put("id", id);
    origen.put("rol", Rol.ESTUDIANTE);
    origen.put("intentos_fallidos", 3);
    origen.put("activo", true);
    origen.put("motivo_baja", null);

    Map<String, Object> limpio = SaneadorDeAuditoria.sanear(origen);

    assertThat(limpio)
        .containsEntry("id", id.toString())
        .containsEntry("rol", "ESTUDIANTE")
        .containsEntry("intentos_fallidos", 3)
        .containsEntry("activo", true)
        .containsEntry("motivo_baja", null);
  }

  @Test
  void devuelveUnaCopiaQueNoCambiaSiElOrigenCambia() {
    Map<String, Object> interno = new LinkedHashMap<>(Map.of("color", "rojo"));

    Map<String, Object> limpio = SaneadorDeAuditoria.sanear(Map.of("datos", interno));
    interno.put("color", "azul");

    assertThat(limpio.get("datos")).isEqualTo(Map.of("color", "rojo"));
  }

  private enum Rol {
    ESTUDIANTE
  }
}
