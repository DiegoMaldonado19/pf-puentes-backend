package com.cunoc.puentes.sync;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IdempotenciaRepository extends JpaRepository<Idempotencia, UUID> {

  @Query(
      value =
          """
          SELECT huella_version AS "huellaVersion",
                 huella_sha256 AS "huellaSha256",
                 estado_http AS "estadoHttp",
                 respuesta_bytes AS "respuestaBytes"
          FROM idempotencia
          WHERE usuario_id = :usuarioId AND clave = :clave
          """,
      nativeQuery = true)
  Optional<ResultadoGuardado> buscarResultado(
      @Param("usuarioId") UUID usuarioId, @Param("clave") String clave);

  interface ResultadoGuardado {

    Integer getHuellaVersion();

    byte[] getHuellaSha256();

    Integer getEstadoHttp();

    byte[] getRespuestaBytes();
  }

  Optional<Idempotencia> findByUsuarioIdAndClave(UUID usuarioId, String clave);
  Idempotencia save(Idempotencia fila);
}
