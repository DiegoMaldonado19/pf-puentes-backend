package com.cunoc.puentes.notificacion;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificacionRepository extends JpaRepository<Notificacion, UUID> {

  Page<Notificacion> findByUsuarioId(UUID usuarioId, Pageable pageable);

  long countByUsuarioIdAndLeidaEnIsNull(UUID usuarioId);

  Optional<Notificacion> findByIdAndUsuarioId(UUID id, UUID usuarioId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT n FROM Notificacion n WHERE n.id = :id AND n.usuarioId = :usuarioId")
  Optional<Notificacion> findByIdAndUsuarioIdParaLectura(
      @Param("id") UUID id, @Param("usuarioId") UUID usuarioId);

  @Query(value = "SELECT COUNT(*) > 0 FROM usuario WHERE id = :usuarioId", nativeQuery = true)
  boolean existeUsuario(@Param("usuarioId") UUID usuarioId);
}
