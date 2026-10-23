package com.cunoc.puentes.formulario;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormularioVersionRepository extends JpaRepository<FormularioVersion, UUID> {

  Optional<FormularioVersion> findByEstado(EstadoFormulario estado);

  Optional<FormularioVersion> findByCodigo(String codigo);

  boolean existsByCodigo(String codigo);

  Page<FormularioVersion> findAllByOrderByCreadoEnDesc(Pageable pageable);
}
