package com.cunoc.puentes.foro;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ComentarioRepository extends JpaRepository<Comentario, UUID> {

  @Query(
      "SELECT c FROM Comentario c WHERE c.hilo.id = :hiloId AND c.respuestaA IS NULL"
          + " AND c.hilo.inspeccionId IS NULL AND c.ocultoEn IS NULL")
  Page<Comentario> listarRaizVisibles(@Param("hiloId") UUID hiloId, Pageable pageable);

  @Query(
      "SELECT c FROM Comentario c WHERE c.hilo.id = :hiloId AND c.respuestaA IS NULL"
          + " AND c.hilo.inspeccionId IS NOT NULL AND c.ocultoEn IS NULL")
  Page<Comentario> listarRaizInspeccionVisibles(@Param("hiloId") UUID hiloId, Pageable pageable);

  @Query(
      "SELECT c FROM Comentario c WHERE c.hilo.id = :hiloId AND c.respuestaA IS NULL"
          + " AND c.hilo.inspeccionId IS NOT NULL AND c.ocultoEn IS NULL"
          + " AND c.elementoRef = :elementoRef")
  Page<Comentario> listarRaizInspeccionVisiblesPorElemento(
      @Param("hiloId") UUID hiloId, @Param("elementoRef") String elementoRef, Pageable pageable);

  @Query(
      value =
          "SELECT c FROM Comentario c WHERE c.respuestaA.id = :padreId"
              + " AND c.hilo.id = c.respuestaA.hilo.id"
              + " AND c.hilo.inspeccionId IS NULL AND c.ocultoEn IS NULL",
      countQuery =
          "SELECT COUNT(c) FROM Comentario c WHERE c.respuestaA.id = :padreId"
              + " AND c.hilo.id = c.respuestaA.hilo.id"
              + " AND c.hilo.inspeccionId IS NULL AND c.ocultoEn IS NULL")
  Page<Comentario> listarRespuestasVisibles(@Param("padreId") UUID padreId, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM Comentario c WHERE c.id = :id")
  Optional<Comentario> buscarParaModificar(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT c FROM Comentario c WHERE c.respuestaA.id = :raizId"
          + " AND c.hilo.id = :hiloId ORDER BY c.id ASC")
  List<Comentario> buscarRespuestasDirectasParaModificar(
      @Param("raizId") UUID raizId, @Param("hiloId") UUID hiloId);
}
