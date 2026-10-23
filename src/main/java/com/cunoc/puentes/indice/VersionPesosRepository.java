package com.cunoc.puentes.indice;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface VersionPesosRepository extends JpaRepository<VersionPesos, UUID> {

  Page<VersionPesos> findByFormularioVersionId(UUID formularioVersionId, Pageable pageable);

  @Query(
      value =
          """
        SELECT f.id, f.codigo FROM formulario_version f
        WHERE EXISTS (SELECT 1 FROM version_pesos p WHERE p.formulario_version_id = f.id)
        ORDER BY f.codigo, f.id
        """,
      countQuery =
          """
        SELECT count(*) FROM formulario_version f
        WHERE EXISTS (SELECT 1 FROM version_pesos p WHERE p.formulario_version_id = f.id)
        """,
      nativeQuery = true)
  Page<FormularioConPesos> formulariosConPesos(Pageable pagina);

  interface FormularioConPesos {
    UUID getId();

    String getCodigo();
  }

  // ADR 0010: consultar el módulo vecino por SQL sin importar su entidad JPA.
  @Query(
      value = "SELECT EXISTS (SELECT 1 FROM formulario_version WHERE id = :id)",
      nativeQuery = true)
  boolean existeFormulario(UUID id);
}
