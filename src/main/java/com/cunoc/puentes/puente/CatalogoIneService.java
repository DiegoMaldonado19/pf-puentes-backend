package com.cunoc.puentes.puente;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.puente.dto.DepartamentoDTO;
import com.cunoc.puentes.puente.dto.MunicipioDTO;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catálogo INE de departamentos y municipios (RN-INV-04). */
@Service
@Transactional(readOnly = true)
public class CatalogoIneService {

  private final DepartamentoRepository departamentos;
  private final MunicipioRepository municipios;
  private final CatalogoIneMapper mapper;

  CatalogoIneService(
      DepartamentoRepository departamentos,
      MunicipioRepository municipios,
      CatalogoIneMapper mapper) {
    this.departamentos = departamentos;
    this.municipios = municipios;
    this.mapper = mapper;
  }

  public List<DepartamentoDTO> listarDepartamentos() {
    return departamentos.findAllByOrderByNombreAsc().stream().map(mapper::aDTO).toList();
  }

  public List<MunicipioDTO> listarMunicipios(String departamentoCodigo) {
    if (!departamentos.existsById(departamentoCodigo)) {
      throw new NegocioException(
          HttpStatus.NOT_FOUND,
          "departamento-no-encontrado",
          "Departamento no encontrado",
          "No existe un departamento con el código indicado.");
    }
    return municipios.findByDepartamentoCodigoOrderByNombreAsc(departamentoCodigo).stream()
        .map(mapper::aDTO)
        .toList();
  }
}
