package com.cunoc.puentes.puente;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Solo lectura: el catálogo INE lo carga una migración (ADR 0011). */
interface MunicipioRepository extends Repository<Municipio, String> {

  List<Municipio> findByDepartamentoCodigoOrderByNombreAsc(String departamentoCodigo);

  Optional<Municipio> findById(String codigo);
}
