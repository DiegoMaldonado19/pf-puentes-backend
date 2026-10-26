package com.cunoc.puentes.indice;

import com.cunoc.puentes.common.NegocioException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lectura interna: la operación consumidora autoriza al actor y valida el formulario completo. */
@Service
public class PreparacionCalificacionesService {
  private final MetadatosDanoFormulario metadatos;
  private final VersionPesosRepository versiones;
  private final PesoElementoRepository pesos;
  private final PesosMapper mapper;
  private final AdaptadorRespuestasDano adaptador = new AdaptadorRespuestasDano();

  PreparacionCalificacionesService(
      MetadatosDanoFormulario metadatos,
      VersionPesosRepository versiones,
      PesoElementoRepository pesos,
      PesosMapper mapper) {
    this.metadatos = metadatos;
    this.versiones = versiones;
    this.pesos = pesos;
    this.mapper = mapper;
  }

  /** Juego explícito, sin seleccionar el último/activo ni escribir resultados de inspección. */
  @Transactional(readOnly = true)
  public List<CalificacionElemento> preparar(
      UUID formularioId, UUID juegoId, Map<String, Object> datos) {
    if (formularioId == null || juegoId == null || datos == null) {
      throw new IllegalArgumentException(
          "Versión del formulario, juego y respuestas son obligatorios.");
    }
    var campos = metadatos.campos(formularioId);
    var version =
        versiones
            .findById(juegoId)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "juego-pesos-no-encontrado",
                        "Juego de pesos no encontrado",
                        "El juego de pesos indicado no existe."));
    if (!formularioId.equals(version.getFormularioVersionId())) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "juego-pesos-de-otra-version",
          "Juego de pesos incompatible",
          "El juego debe pertenecer a la versión del formulario indicada.");
    }
    JuegoPesos juego;
    try {
      juego =
          mapper.aJuego(
              version, pesos.findByVersionPesosId(juegoId, Pageable.unpaged()).getContent());
    } catch (IllegalArgumentException error) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "juego-pesos-incompatible",
          "Juego de pesos incompatible",
          "El juego persistido debe contener pesos válidos y referencias únicas.");
    }
    return adaptador.adaptar(campos, juego, datos);
  }
}
