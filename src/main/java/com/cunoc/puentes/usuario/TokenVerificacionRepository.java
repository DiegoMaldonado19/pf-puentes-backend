package com.cunoc.puentes.usuario;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TokenVerificacionRepository extends JpaRepository<TokenVerificacion, UUID> {

  // DT-SEC-12: solo se busca por el hash; el valor del token nunca llega a la base
  Optional<TokenVerificacion> findByTokenHash(String tokenHash);

  /** Vigente: sin usar y que vence después de {@code ahora}. */
  @Query(
      """
      SELECT t FROM TokenVerificacion t
      WHERE t.usuario.id = :usuarioId AND t.tipo = :tipo
        AND t.usadoEn IS NULL AND t.expiraEn > :ahora
      """)
  List<TokenVerificacion> buscarVigentes(UUID usuarioId, TipoTokenVerificacion tipo, Instant ahora);
}
