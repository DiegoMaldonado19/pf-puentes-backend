package com.cunoc.puentes.sync;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface IdempotenciaRepository extends Repository<Idempotencia, UUID> {

  Idempotencia save(Idempotencia fila);

  Optional<Idempotencia> findByUsuarioIdAndClave(UUID usuarioId, String clave);
}
