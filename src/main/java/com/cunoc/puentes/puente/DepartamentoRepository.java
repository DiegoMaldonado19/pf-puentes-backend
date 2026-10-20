package com.cunoc.puentes.puente;

import java.util.List;
import org.springframework.data.repository.Repository;

/** Solo lectura: el catálogo INE lo carga una migración (ADR 0011). */
interface DepartamentoRepository extends Repository<Departamento, String> {

  List<Departamento> findAllByOrderByNombreAsc();

  boolean existsById(String codigo);
}
