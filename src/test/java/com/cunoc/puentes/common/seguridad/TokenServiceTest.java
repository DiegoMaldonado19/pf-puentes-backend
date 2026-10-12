package com.cunoc.puentes.common.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.usuario.Rol;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class TokenServiceTest {

  private static final String SECRETO = SeguridadDePrueba.SECRETO;
  private static final Instant AHORA = Instant.parse("2026-10-11T15:00:00Z");
  private static final UUID USUARIO = UUID.fromString("0192f5a0-0000-7000-8000-000000000002");

  private final TokenService servicio = new TokenService(SECRETO, reloj(AHORA));

  @Test
  void elTokenDuraQuinceMinutosYSoloLlevaIdRolEmisionYExpiracion() {
    String token = servicio.emitirAcceso(USUARIO, Rol.CATEDRATICO);

    Map<String, Object> payload = payloadDe(token);
    assertThat(payload.keySet()).containsExactlyInAnyOrder("sub", "rol", "iat", "exp");
    assertThat(payload.get("sub")).isEqualTo(USUARIO.toString());
    assertThat(payload.get("rol")).isEqualTo("CATEDRATICO");
    assertThat(((Number) payload.get("iat")).longValue()).isEqualTo(AHORA.getEpochSecond());
    assertThat(((Number) payload.get("exp")).longValue())
        .isEqualTo(AHORA.plusSeconds(15 * 60).getEpochSecond());
  }

  @Test
  void validaUnTokenVigente() {
    String token = servicio.emitirAcceso(USUARIO, Rol.ESTUDIANTE);

    assertThat(servicio.validar(token)).contains(new TokenAcceso(USUARIO, Rol.ESTUDIANTE));
  }

  @Test
  void seVenceALosQuinceMinutos() {
    String token = servicio.emitirAcceso(USUARIO, Rol.ESTUDIANTE);

    var casiVencido = new TokenService(SECRETO, reloj(AHORA.plusSeconds(15 * 60 - 1)));
    var vencido = new TokenService(SECRETO, reloj(AHORA.plusSeconds(15 * 60 + 1)));

    assertThat(casiVencido.validar(token)).isPresent();
    assertThat(vencido.validar(token)).isEmpty();
  }

  @Test
  void rechazaUnTokenConLaFirmaAlterada() {
    String token = servicio.emitirAcceso(USUARIO, Rol.ESTUDIANTE);
    String[] partes = token.split("\\.");
    String payloadAdmin =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8)
                    .replace("ESTUDIANTE", "ADMINISTRADOR")
                    .getBytes(StandardCharsets.UTF_8));

    assertThat(servicio.validar(partes[0] + "." + payloadAdmin + "." + partes[2])).isEmpty();
    assertThat(servicio.validar(partes[0] + "." + partes[1] + ".AAAA")).isEmpty();
  }

  @Test
  void rechazaUnTokenFirmadoConOtraClave() {
    var otro = new TokenService("otra-clave-distinta-de-al-menos-32-bytes", reloj(AHORA));

    assertThat(servicio.validar(otro.emitirAcceso(USUARIO, Rol.ADMINISTRADOR))).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "no-es-un-jwt", "a.b.c", "a.b", "Bearer abc"})
  void rechazaUnTokenMalFormado(String token) {
    assertThat(servicio.validar(token)).isEmpty();
  }

  @Test
  void rechazaUnTokenSinFirma() {
    String sinFirma =
        Jwts.builder()
            .subject(USUARIO.toString())
            .claim("rol", "ADMINISTRADOR")
            .expiration(Date.from(AHORA.plusSeconds(60)))
            .compact();

    assertThat(servicio.validar(sinFirma)).isEmpty();
  }

  @Test
  void rechazaUnTokenFirmadoConOtroAlgoritmo() {
    // Con una clave de 64 bytes (p. ej. openssl rand -hex 32) JJWT también acepta HS512
    String secretoLargo = "s".repeat(64);
    String hs512 =
        Jwts.builder()
            .subject(USUARIO.toString())
            .claim("rol", "ESTUDIANTE")
            .expiration(Date.from(AHORA.plusSeconds(60)))
            .signWith(
                Keys.hmacShaKeyFor(secretoLargo.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS512)
            .compact();
    var conClaveLarga = new TokenService(secretoLargo, reloj(AHORA));

    assertThat(conClaveLarga.validar(hs512)).isEmpty();
    assertThat(conClaveLarga.validar(conClaveLarga.emitirAcceso(USUARIO, Rol.ESTUDIANTE)))
        .isPresent();
  }

  @Test
  void rechazaUnTokenConIdORolInvalidos() {
    assertThat(servicio.validar(firmado("no-es-uuid", "ESTUDIANTE"))).isEmpty();
    assertThat(servicio.validar(firmado(USUARIO.toString(), "SUPERUSUARIO"))).isEmpty();
    assertThat(servicio.validar(firmado(USUARIO.toString(), null))).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"muy-corta", "12345678901234567890123456789 "})
  void noArrancaConUnaClaveDebil(String secreto) {
    assertThatThrownBy(() -> new TokenService(secreto, reloj(AHORA)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET")
        .hasMessageContaining("32 bytes")
        .hasMessageNotContaining("muy-corta");
  }

  @Test
  void aceptaUnaClaveDeExactamente32Bytes() {
    var servicio32 = new TokenService("a".repeat(32), reloj(AHORA));

    assertThat(servicio32.validar(servicio32.emitirAcceso(USUARIO, Rol.ESTUDIANTE))).isPresent();
  }

  private static String firmado(String sujeto, String rol) {
    var builder = Jwts.builder().subject(sujeto).expiration(Date.from(AHORA.plusSeconds(60)));
    if (rol != null) {
      builder.claim("rol", rol);
    }
    return builder
        .signWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
        .compact();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> payloadDe(String token) {
    byte[] json = Base64.getUrlDecoder().decode(token.split("\\.")[1]);
    return JsonMapper.builder().build().readValue(json, Map.class);
  }

  private static Clock reloj(Instant instante) {
    return Clock.fixed(instante, ZoneOffset.UTC);
  }
}
