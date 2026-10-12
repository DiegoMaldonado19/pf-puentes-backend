package com.cunoc.puentes.usuario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface InvitacionRepository extends JpaRepository<Invitacion, UUID> {

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

  // DT-BE-09: el administrador viaja en la misma consulta
  @EntityGraph(attributePaths = "administrador")
  List<Invitacion> findAllByOrderByCreadoEnDesc();
}
