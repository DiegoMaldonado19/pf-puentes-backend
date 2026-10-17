package com.cunoc.puentes.usuario;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface TokenRenovacionRepository extends JpaRepository<TokenRenovacion, UUID> {

  // DT-SEC-12: solo se busca por el hash; el valor del token nunca llega a la base
  Optional<TokenRenovacion> findByTokenHash(String tokenHash);

  /**
   * Vigente: sin revocar y que vence después de {@code ahora}. Trae la cuenta en la misma consulta.
   */
  @Query(
      """
      SELECT t FROM TokenRenovacion t JOIN FETCH t.usuario
      WHERE t.tokenHash = :tokenHash AND t.revocadoEn IS NULL AND t.expiraEn > :ahora
      """)
  Optional<TokenRenovacion> buscarVigente(String tokenHash, Instant ahora);

  /** Revoca ese token; 0 si ya estaba revocado o no existe. */
  @Modifying
  @Query(
      """
      UPDATE TokenRenovacion t SET t.revocadoEn = :ahora, t.actualizadoEn = :ahora
      WHERE t.tokenHash = :tokenHash AND t.revocadoEn IS NULL
      """)
  int revocar(String tokenHash, Instant ahora);

  /** Revoca todos los tokens sin revocar de la cuenta. */
  @Modifying
  @Query(
      """
      UPDATE TokenRenovacion t SET t.revocadoEn = :ahora, t.actualizadoEn = :ahora
      WHERE t.usuario.id = :usuarioId AND t.revocadoEn IS NULL
      """)
  int revocarTodas(UUID usuarioId, Instant ahora);
}
