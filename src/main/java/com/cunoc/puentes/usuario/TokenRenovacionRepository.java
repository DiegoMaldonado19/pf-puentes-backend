package com.cunoc.puentes.usuario;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TokenRenovacionRepository extends JpaRepository<TokenRenovacion, UUID> {

  // DT-SEC-12: solo se busca por el hash; el valor del token nunca llega a la base
  Optional<TokenRenovacion> findByTokenHash(String tokenHash);
}
