package com.cunoc.puentes.common.seguridad;

import com.cunoc.puentes.usuario.Rol;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Emite y valida los JWT de acceso: HS256, 15 minutos y sin datos personales (DT-SEC-01). La
 * renovación y su tabla son de US-P2-07.
 */
@Service
class TokenService {

  static final Duration VIGENCIA = Duration.ofMinutes(15);

  private static final int MIN_BYTES_CLAVE = 32;
  private static final String RECLAMO_ROL = "rol";

  private final SecretKey clave;
  private final Clock reloj;

  @Autowired
  TokenService(@Value("${sgp.jwt.secreto}") String secreto) {
    this(secreto, Clock.systemUTC());
  }

  TokenService(String secreto, Clock reloj) {
    // El mensaje nunca incluye el valor de la clave (DT-SEC-12)
    byte[] bytes = secreto == null ? new byte[0] : secreto.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < MIN_BYTES_CLAVE) {
      throw new IllegalStateException(
          "JWT_SECRET no está definida o tiene menos de %d bytes; HS256 exige al menos esa longitud."
              .formatted(MIN_BYTES_CLAVE));
    }
    this.clave = Keys.hmacShaKeyFor(bytes);
    this.reloj = reloj;
  }

  String emitirAcceso(UUID usuarioId, Rol rol) {
    Instant ahora = reloj.instant();
    return Jwts.builder()
        .subject(usuarioId.toString())
        .claim(RECLAMO_ROL, rol.name())
        .issuedAt(Date.from(ahora))
        .expiration(Date.from(ahora.plus(VIGENCIA)))
        .signWith(clave, Jwts.SIG.HS256)
        .compact();
  }

  /** Vacío ante cualquier defecto; quien llama no debe distinguir el motivo (RN-USR-10). */
  Optional<TokenAcceso> validar(String token) {
    try {
      var jws =
          Jwts.parser()
              .verifyWith(clave)
              .clock(() -> Date.from(reloj.instant()))
              .build()
              .parseSignedClaims(token);
      // Una clave larga también valida HS384/HS512; el contrato es solo HS256
      if (!Jwts.SIG.HS256.getId().equals(jws.getHeader().getAlgorithm())) {
        return Optional.empty();
      }
      return Optional.of(
          new TokenAcceso(
              UUID.fromString(jws.getPayload().getSubject()),
              Rol.valueOf(jws.getPayload().get(RECLAMO_ROL, String.class))));
    } catch (RuntimeException e) {
      // JwtException, IllegalArgumentException (mal formado, sub o rol inválidos) y nulos
      return Optional.empty();
    }
  }
}
