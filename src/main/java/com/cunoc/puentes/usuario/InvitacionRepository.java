package com.cunoc.puentes.usuario;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface InvitacionRepository
    extends JpaRepository<Invitacion, UUID>, JpaSpecificationExecutor<Invitacion> {

  /**
   * La invitación abierta del correo: ni aceptada ni revocada. Es única por invitacion_abierta_uk.
   * Que haya vencido no la cierra: la revoca el servicio antes de reinvitar.
   */
  @Query(
      """
      SELECT i FROM Invitacion i
      WHERE lower(i.correo) = lower(:correo) AND i.aceptadaEn IS NULL AND i.revocadaEn IS NULL
      """)
  Optional<Invitacion> buscarAbierta(String correo);

  /**
   * RN-USR-03: la fila queda bloqueada, así dos aceptaciones del mismo enlace no crean dos cuentas.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Invitacion> findByTokenHash(String tokenHash);

  // DT-BE-09: el administrador viaja en la misma consulta
  @EntityGraph(attributePaths = "administrador")
  List<Invitacion> findAllByOrderByCreadoEnDesc();

  // DT-BE-08, DT-BE-09: el listado de la API es paginado y trae al administrador sin N+1
  @Override
  @EntityGraph(attributePaths = "administrador")
  Page<Invitacion> findAll(Specification<Invitacion> filtro, Pageable pagina);
}
