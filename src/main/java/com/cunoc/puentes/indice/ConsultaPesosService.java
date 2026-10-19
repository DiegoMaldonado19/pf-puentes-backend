package com.cunoc.puentes.indice;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.indice.dto.JuegoPesosDTO;
import com.cunoc.puentes.indice.dto.PesoElementoDTO;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class ConsultaPesosService {

  private final VersionPesosRepository versiones;
  private final PesoElementoRepository pesos;
  private final PesosMapper mapper;

  ConsultaPesosService(
      VersionPesosRepository versiones, PesoElementoRepository pesos, PesosMapper mapper) {
    this.versiones = versiones;
    this.pesos = pesos;
    this.mapper = mapper;
  }

  public Page<JuegoPesosDTO> listar(UUID formularioId, int pagina, int tamano) {
    if (!versiones.existeFormulario(formularioId)) {
      throw new NegocioException(
          HttpStatus.NOT_FOUND,
          "formulario-no-encontrado",
          "Formulario no encontrado",
          "La versión del formulario indicada no existe.");
    }
    return versiones
        .findByFormularioVersionId(
            formularioId, PageRequest.of(pagina, tamano, Sort.by("id").descending()))
        .map(mapper::aDTO);
  }

  public JuegoPesosDTO obtener(UUID id) {
    return mapper.aDTO(buscar(id));
  }

  public Page<PesoElementoDTO> listarPesos(UUID id, int pagina, int tamano) {
    buscar(id);
    return pesos
        .findByVersionPesosId(id, PageRequest.of(pagina, tamano, Sort.by("elementoRef")))
        .map(mapper::aDTO);
  }

  private VersionPesos buscar(UUID id) {
    return versiones
        .findById(id)
        .orElseThrow(
            () ->
                new NegocioException(
                    HttpStatus.NOT_FOUND,
                    "juego-pesos-no-encontrado",
                    "Juego de pesos no encontrado",
                    "El juego de pesos indicado no existe."));
  }
}
